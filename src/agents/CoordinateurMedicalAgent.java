package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MEMBRE A — Agent Coordinateur Médical
 * Rôle : Courtier entre ambulances et hôpitaux.
 *   - Maintient le registre des lits disponibles par hôpital.
 *   - Oriente les ambulances vers l'hôpital le plus proche avec des lits.
 *   - Alerte le Répartiteur en cas de saturation globale.
 */
public class CoordinateurMedicalAgent extends Agent {

    // Registre des hôpitaux : nom → {lits disponibles, posX, posY}
    private final Map<String, int[]> hopitaux = new HashMap<>();
    // hopitaux.get("HopitalA") = [litsDisponibles, posX, posY]

    private AID repartiteur;

    @Override
    protected void setup() {
        System.out.println("[COORD_MEDICAL] Démarré.");

        // Initialiser quelques hôpitaux de la ville
        hopitaux.put("HopitalA", new int[]{10,  2,  3});
        hopitaux.put("HopitalB", new int[]{ 5, 15,  8});
        hopitaux.put("HopitalC", new int[]{ 8,  7, 17});

        enregistrerDF();

        // Écoute des ambulances qui demandent un hôpital
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    traiterDemandeHopital(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("CoordinateurMedical");
        sd.setName("Groupe-Medical");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[COORD_MEDICAL] Erreur DF : " + e.getMessage());
        }
    }

    private void traiterDemandeHopital(ACLMessage msg) {
        // Format attendu : "DEMANDE_HOPITAL;ambulanceX;ambulanceY"
        String contenu = msg.getContent();
        if (contenu == null || !contenu.startsWith("DEMANDE_HOPITAL")) return;

        String[] parts = contenu.split(";");
        int ambX = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int ambY = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

        String hopitalChoisi = trouverHopitalDisponible(ambX, ambY);
        ACLMessage reponse = msg.createReply();

        if (hopitalChoisi != null) {
            int[] info = hopitaux.get(hopitalChoisi);
            // Décrémenter un lit
            info[0] = Math.max(0, info[0] - 1);

            reponse.setPerformative(ACLMessage.INFORM);
            reponse.setContent("HOPITAL;" + hopitalChoisi + ";" + info[1] + ";" + info[2]);
            System.out.println("[COORD_MEDICAL] Ambulance orientée vers " + hopitalChoisi
                + " (lits restants: " + info[0] + ")");

            verifierSaturation();
        } else {
            reponse.setPerformative(ACLMessage.FAILURE);
            reponse.setContent("SATURATION_GLOBALE");
            System.err.println("[COORD_MEDICAL] SATURATION — aucun lit disponible !");
            alerterRepartiteur();
        }

        send(reponse);
    }

    private String trouverHopitalDisponible(int ambX, int ambY) {
        String meilleur = null;
        double distMin = Double.MAX_VALUE;

        for (Map.Entry<String, int[]> entry : hopitaux.entrySet()) {
            int[] info = entry.getValue();
            int lits = info[0];
            int hX   = info[1];
            int hY   = info[2];

            if (lits > 0) {
                double dist = UrbanGrid.distance(ambX, ambY, hX, hY);
                if (dist < distMin) {
                    distMin  = dist;
                    meilleur = entry.getKey();
                }
            }
        }
        return meilleur;
    }

    private void verifierSaturation() {
        boolean saturé = hopitaux.values().stream().allMatch(v -> v[0] == 0);
        if (saturé) alerterRepartiteur();
    }

    private void alerterRepartiteur() {
        if (repartiteur == null) repartiteur = chercherRepartiteur();
        if (repartiteur == null) return;

        ACLMessage alerte = new ACLMessage(ACLMessage.INFORM);
        alerte.addReceiver(repartiteur);
        alerte.setContent("SATURATION_HOPITAUX");
        alerte.setOntology("EmergencyOntology");
        send(alerte);
        System.out.println("[COORD_MEDICAL] Alerte de saturation envoyée au Répartiteur.");
    }

    private AID chercherRepartiteur() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Repartiteur");
        template.addServices(sd);
        try {
            DFAgentDescription[] r = DFService.search(this, template);
            if (r.length > 0) return r[0].getName();
        } catch (FIPAException e) {
            System.err.println("[COORD_MEDICAL] Erreur recherche DF : " + e.getMessage());
        }
        return null;
    }

    // Méthode publique pour libérer un lit (appelée via message ou simulation)
    public void libererLit(String nomHopital) {
        if (hopitaux.containsKey(nomHopital)) {
            hopitaux.get(nomHopital)[0]++;
            System.out.println("[COORD_MEDICAL] Lit libéré à " + nomHopital);
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[COORD_MEDICAL] Arrêté.");
    }
}
