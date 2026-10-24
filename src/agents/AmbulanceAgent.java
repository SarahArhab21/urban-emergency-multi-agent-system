package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * MEMBRE C — Agent Unité d'Ambulance
 * Spécialité : Incidents MEDICAL
 * FSM : INACTIF → EN_DEPLACEMENT → SUR_LIEUX → RETOUR → INACTIF
 * Format PROPOSE : "INACTIF;DISTANCE;X;Y"
 */
public class AmbulanceAgent extends Agent {

    // ─── États de la machine à états ───
    public enum Etat { INACTIF, EN_DEPLACEMENT, SUR_LIEUX, RETOUR }

    private Etat etatCourant = Etat.INACTIF;

    // Position dynamique (mise à jour par TickBehaviour)
    private int posX, posY;

    // Destination courante
    private int cibleX = -1, cibleY = -1;

    // AID du répartiteur qui a envoyé l'ACCEPT
    private AID repartiteurAssignant;

    // Identifiant de la mission courante
    private String convIdMission;

    // Base de retour (position initiale)
    private int baseX, baseY;

    @Override
    protected void setup() {
        int[] pos = UrbanGrid.randomPosition();
        posX = pos[0]; posY = pos[1];
        baseX = posX;  baseY = posY;

        System.out.println("[AMBULANCE] " + getLocalName()
            + " démarrée en (" + posX + "," + posY + ")");

        enregistrerDF();

        // Comportement principal : écoute CFP / ACCEPT / REJECT
        addBehaviour(new EcouteCFP());

        // Déplacement pas à pas toutes les 1 seconde
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                deplacerUnPas();
            }
        });
    }

    // ─── Enregistrement dans le DF ───
    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("MEDICAL");
        sd.setName("Groupe-Urgence");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[AMBULANCE] Enregistrée dans DF (MEDICAL).");
        } catch (FIPAException e) {
            System.err.println("[AMBULANCE] Erreur DF : " + e.getMessage());
        }
    }

    // ─── Déplacement pas à pas selon l'état ───
    private void deplacerUnPas() {
        switch (etatCourant) {
            case EN_DEPLACEMENT:
                if (cibleX >= 0) {
                    int[] next = UrbanGrid.moveToward(posX, posY, cibleX, cibleY);
                    posX = next[0]; posY = next[1];
                    if (posX == cibleX && posY == cibleY) {
                        // Arrivée sur les lieux
                        etatCourant = Etat.SUR_LIEUX;
                        informerArrivee();
                    }
                }
                break;
            case RETOUR:
                int[] next = UrbanGrid.moveToward(posX, posY, baseX, baseY);
                posX = next[0]; posY = next[1];
                if (posX == baseX && posY == baseY) {
                    etatCourant = Etat.INACTIF;
                    System.out.println("[AMBULANCE] " + getLocalName() + " de retour à la base.");
                }
                break;
            default:
                break;
        }
    }

    // ─── Informer le répartiteur de l'arrivée ───
    private void informerArrivee() {
        System.out.println("[AMBULANCE] " + getLocalName() + " sur les lieux en ("
            + posX + "," + posY + ")");
        if (repartiteurAssignant != null) {
            ACLMessage info = new ACLMessage(ACLMessage.INFORM);
            info.addReceiver(repartiteurAssignant);
            info.setContent("ARRIVEE;" + posX + ";" + posY);
            info.setConversationId(convIdMission);
            info.setOntology("EmergencyOntology");
            send(info);
        }
        // Simuler intervention puis retour (après 5 secondes via WakerBehaviour)
        addBehaviour(new WakerBehaviour(this, 5000) {
            @Override
            protected void onWake() {
                terminerIntervention();
            }
        });
    }

    // ─── Fin de l'intervention ───
    private void terminerIntervention() {
        System.out.println("[AMBULANCE] " + getLocalName() + " — intervention terminée.");
        if (repartiteurAssignant != null) {
            ACLMessage info = new ACLMessage(ACLMessage.INFORM);
            info.addReceiver(repartiteurAssignant);
            info.setContent("RESOLU;" + posX + ";" + posY);
            info.setConversationId(convIdMission);
            info.setOntology("EmergencyOntology");
            send(info);
        }
        etatCourant = Etat.RETOUR;
        cibleX = -1; cibleY = -1;
        repartiteurAssignant = null;
    }

    // ─── Comportement : écoute CFP du Répartiteur ───
    private class EcouteCFP extends CyclicBehaviour {
        private static final long serialVersionUID = 1L; // ← ajouter ici

        @Override
        public void action() {
            // 1. Écouter les CFP
            MessageTemplate mtCFP = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage cfp = myAgent.receive(mtCFP);
            if (cfp != null) {
                traiterCFP(cfp);
                return;
            }

            // 2. Écouter les ACCEPT / REJECT
            MessageTemplate mtAR = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
            );
            ACLMessage rep = myAgent.receive(mtAR);
            if (rep != null) {
                traiterReponse(rep);
                return;
            }

            block();
        }
        
        
        protected void onTick() {
            // 1. Utilise les variables globales de ton agent (celles qui ont reçu les données du message ACL)
            // Supprime les lignes "int destX;" etc., car elles cachent les vraies données.

            // 2. Calcul du mouvement vers la destination de l'incident
        	int[] nextPos = UrbanGrid.moveToward(posX, posY, cibleX, cibleY);            posX = nextPos[0];
            posY = nextPos[1];

            // 3. Log pour la console
            System.out.println("[" + getLocalName() + "] En route... Position : (" + posX + "," + posY + ")");

            // 4. Affichage de la carte (seulement pour Ambulance1 pour éviter de surcharger la console)
            if (getLocalName().equals("Ambulance1")) {
                // On passe les positions actuelles et les cibles à la méthode de dessin
            	UrbanGrid.dessinerVille(posX, posY, 0, 0, cibleX, cibleY, 0, 0);            }
        }
        
        

        private void traiterCFP(ACLMessage cfp) {
            String contenu = cfp.getContent();
            if (contenu == null) return;
            String[] parts = contenu.split(";");
            if (parts.length < 5) return;

            String type = parts[0];

            // L'ambulance répond uniquement aux incidents MEDICAL
            if (!"MEDICAL".equals(type)) return;

            if (etatCourant != Etat.INACTIF) {
                // Refuser si occupé
                ACLMessage refus = cfp.createReply();
                refus.setPerformative(ACLMessage.REFUSE);
                refus.setContent("EN_COURS");
                send(refus);
                return;
            }

            // Calculer la distance à l'incident
            int incX = Integer.parseInt(parts[2]);
            int incY = Integer.parseInt(parts[3]);
            double dist = UrbanGrid.distance(posX, posY, incX, incY);

            // Répondre avec PROPOSE : "INACTIF;DISTANCE;posX;posY"
            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.format(java.util.Locale.US, "INACTIF;%.2f;%d;%d", dist, posX, posY));            propose.setOntology("EmergencyOntology");
            send(propose);

            System.out.println("[AMBULANCE] " + myAgent.getLocalName()
                + " PROPOSE pour incident MEDICAL — dist=" + String.format("%.2f", dist));
        }

        private void traiterReponse(ACLMessage msg) {
            if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                String contenu = msg.getContent();
                String[] parts = (contenu != null) ? contenu.split(";") : new String[0];

                if (parts.length >= 2) {
                    cibleX = Integer.parseInt(parts[0]);
                    cibleY = Integer.parseInt(parts[1]);
                } else {
                    // Destination dans le contenu original du CFP (cas simple)
                    cibleX = 0; cibleY = 0;
                }

                repartiteurAssignant = msg.getSender();
                convIdMission = msg.getConversationId();
                etatCourant = Etat.EN_DEPLACEMENT;

                System.out.println("[AMBULANCE] " + myAgent.getLocalName()
                    + " ACCEPTÉ → en route vers (" + cibleX + "," + cibleY + ")");
            } else {
                System.out.println("[AMBULANCE] " + myAgent.getLocalName()
                    + " REJETÉ — reste en attente.");
            }
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[AMBULANCE] " + getLocalName() + " arrêtée.");
    }
}
