package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.UUID;

/**
 * MEMBRE B — Agent Capteur (Sensor Agent)
 * Rôle : Génère des incidents aléatoires et alerte le Répartiteur.
 * Format message envoyé : "TYPE;GRAVITE;X;Y;ID_UNIQUE"
 *   Types    : FIRE | MEDICAL | STRUCTURAL_COLLAPSE | BIOHAZARD | CRYOGENIC_LEAK
 *   Gravité  : LOW | MEDIUM | HIGH | CRITICAL
 */
public class CapteurAgent extends Agent {

    private static final String[] TYPES    = {
        "FIRE", "MEDICAL", "STRUCTURAL_COLLAPSE"
    };
    private static final String[] GRAVITES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};

    // Position fixe du capteur (lieu géographique surveillé)
    private int posX;
    private int posY;

    @Override
    protected void setup() {
        // Récupérer arguments optionnels (x, y) passés au lancement
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            posX = Integer.parseInt(args[0].toString());
            posY = Integer.parseInt(args[1].toString());
        } else {
            int[] pos = UrbanGrid.randomPosition();
            posX = pos[0];
            posY = pos[1];
        }

        System.out.println("[CAPTEUR] " + getLocalName()
            + " démarré en (" + posX + "," + posY + ")");

        // S'enregistrer dans le DF
        enregistrerDF();

        // Générer un incident toutes les 8 secondes
        addBehaviour(new TickerBehaviour(this, 8000) {
            @Override
            protected void onTick() {
                genererIncident();
            }
        });
    }

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Capteur");
        sd.setName("Groupe-Surveillance");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[CAPTEUR] Erreur DF : " + e.getMessage());
        }
    }

    private void genererIncident() {
        String type    = TYPES   [(int)(Math.random() * TYPES.length)];
        String gravite = GRAVITES[(int)(Math.random() * GRAVITES.length)];
        String id      = "inc_" + UUID.randomUUID().toString().substring(0, 8);

        // Pour les capteurs spécialisés, on peut forcer le type via args[2]
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            type = args[2].toString();
        }

        String contenu = type + ";" + gravite + ";" + posX + ";" + posY + ";" + id;
        System.out.println("[CAPTEUR] " + getLocalName() + " → incident : " + contenu);

        // Chercher le Répartiteur dans le DF
        AID repartiteur = chercherRepartiteur();
        if (repartiteur == null) {
            System.err.println("[CAPTEUR] Aucun Répartiteur trouvé dans le DF !");
            return;
        }

        // Envoyer INFORM au Répartiteur
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(repartiteur);
        msg.setContent(contenu);
        msg.setConversationId("incident-" + id);
        msg.setOntology("EmergencyOntology");
        send(msg);
    }

    private AID chercherRepartiteur() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Repartiteur");
        template.addServices(sd);
        try {
            DFAgentDescription[] resultats = DFService.search(this, template);
            if (resultats.length > 0) return resultats[0].getName();
        } catch (FIPAException e) {
            System.err.println("[CAPTEUR] Erreur recherche DF : " + e.getMessage());
        }
        return null;
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[CAPTEUR] " + getLocalName() + " arrêté.");
    }
}
