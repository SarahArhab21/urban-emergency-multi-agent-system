package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * MEMBRE A — Agent Contrôleur de Trafic
 * Rôle : Ouvre des corridors d'urgence pour réduire le temps de trajet.
 * Format REQUEST reçu : "CORRIDOR;startX;startY;endX;endY;convId"
 * Broadcast aux unités : "CORRIDOR_OUVERT;startX;startY;endX;endY;timeout_ms"
 */
public class ControleurTraficAgent extends Agent {

    // Corridors actifs (par convId)
    private final Set<String> corridorsActifs = new HashSet<>();

    @Override
    protected void setup() {
        System.out.println("[TRAFIC] Contrôleur de trafic démarré.");
        enregistrerDF();

        // Écouter les demandes de corridor du Répartiteur
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    traiterDemandeCorridor(msg);
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
        sd.setType("ControleurTrafic");
        sd.setName("Groupe-Coordination");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[TRAFIC] Erreur DF : " + e.getMessage());
        }
    }

    private void traiterDemandeCorridor(ACLMessage msg) {
        String contenu = msg.getContent();
        if (contenu == null || !contenu.startsWith("CORRIDOR")) return;

        // Format : "CORRIDOR;startX;startY;endX;endY"
        String[] parts = contenu.split(";");
        if (parts.length < 5) return;

        String convId    = msg.getConversationId();
        String startX    = parts[1];
        String startY    = parts[2];
        String endX      = parts[3];
        String endY      = parts[4];
        long   timeoutMs = 30000; // corridor ouvert 30 secondes

        System.out.println("[TRAFIC] Ouverture corridor : ("
            + startX + "," + startY + ") → (" + endX + "," + endY + ")");

        corridorsActifs.add(convId);

        // Confirmer au répartiteur
        ACLMessage reponse = msg.createReply();
        reponse.setPerformative(ACLMessage.AGREE);
        reponse.setContent("CORRIDOR_CONFIRME;" + startX + ";" + startY + ";"
            + endX + ";" + endY + ";" + timeoutMs);
        send(reponse);

        // Broadcaster à toutes les unités du DF
        diffuserCorridor(startX, startY, endX, endY, timeoutMs, convId);

        // Fermer le corridor après timeout
        addBehaviour(new WakerBehaviour(this, timeoutMs) {
            @Override
            protected void onWake() {
                fermerCorridor(convId, startX, startY, endX, endY);
            }
        });
    }

    private void diffuserCorridor(String sX, String sY, String eX, String eY,
                                   long timeout, String convId) {
        // Chercher toutes les unités d'intervention dans le DF
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Groupe-Urgence");
        template.addServices(sd);

        try {
            DFAgentDescription[] unites = DFService.search(this, template);
            if (unites.length == 0) return;

            ACLMessage broadcast = new ACLMessage(ACLMessage.INFORM);
            for (DFAgentDescription u : unites) {
                broadcast.addReceiver(u.getName());
            }
            broadcast.setContent("CORRIDOR_OUVERT;" + sX + ";" + sY + ";"
                + eX + ";" + eY + ";" + timeout);
            broadcast.setConversationId(convId);
            broadcast.setOntology("EmergencyOntology");
            send(broadcast);

            System.out.println("[TRAFIC] Corridor diffusé à "
                + unites.length + " unités.");
        } catch (FIPAException e) {
            System.err.println("[TRAFIC] Erreur diffusion : " + e.getMessage());
        }
    }

    private void fermerCorridor(String convId, String sX, String sY, String eX, String eY) {
        corridorsActifs.remove(convId);
        System.out.println("[TRAFIC] Corridor fermé : (" + sX + "," + sY
            + ") → (" + eX + "," + eY + ")");

        // Informer les unités de la fermeture
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Groupe-Urgence");
        template.addServices(sd);

        try {
            DFAgentDescription[] unites = DFService.search(this, template);
            if (unites.length == 0) return;

            ACLMessage fermeture = new ACLMessage(ACLMessage.INFORM);
            for (DFAgentDescription u : unites) fermeture.addReceiver(u.getName());
            fermeture.setContent("CORRIDOR_FERME;" + convId);
            fermeture.setOntology("EmergencyOntology");
            send(fermeture);
        } catch (FIPAException ignored) {}
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[TRAFIC] Arrêté.");
    }
}
