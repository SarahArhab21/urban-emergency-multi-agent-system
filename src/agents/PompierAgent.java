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
 * MEMBRE C — Agent Camion de Pompiers
 * Spécialités : FIRE, STRUCTURAL_COLLAPSE (RESCUE)
 * FSM : INACTIF → EN_DEPLACEMENT → ACTIF (extinction) → RETOUR → INACTIF
 * Abandon si réserve d'eau = 0 → INFORM ABORT au répartiteur
 */
public class PompierAgent extends Agent {

    public enum Etat { INACTIF, EN_DEPLACEMENT, ACTIF, RETOUR }

    private Etat   etatCourant     = Etat.INACTIF;
    private int    reserveEau      = 100; // en % — diminue pendant l'intervention
    private int    posX, posY;
    private int    baseX, baseY;
    private int    cibleX = -1, cibleY = -1;
    private String typeIncidentCourant = "";

    private AID    repartiteurAssignant;
    private String convIdMission;

    @Override
    protected void setup() {
        int[] pos = UrbanGrid.randomPosition();
        posX = pos[0]; posY = pos[1];
        baseX = posX;  baseY = posY;

        System.out.println("[POMPIER] " + getLocalName()
            + " démarré en (" + posX + "," + posY + ") — Eau: " + reserveEau + "%");

        enregistrerDF();

        addBehaviour(new EcouteCFP());

        // Déplacement + gestion réserve d'eau
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                deplacerEtAgir();
            }
        });
    }

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        // Enregistrement double : FIRE et RESCUE
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType("FIRE");
        sd1.setName("Groupe-Urgence");

        ServiceDescription sd2 = new ServiceDescription();
        sd2.setType("RESCUE");
        sd2.setName("Groupe-Urgence");

        dfd.addServices(sd1);
        dfd.addServices(sd2);

        try {
            DFService.register(this, dfd);
            System.out.println("[POMPIER] Enregistré dans DF (FIRE + RESCUE).");
        } catch (FIPAException e) {
            System.err.println("[POMPIER] Erreur DF : " + e.getMessage());
        }
    }

    private void deplacerEtAgir() {
        switch (etatCourant) {
            case EN_DEPLACEMENT:
                int[] next = UrbanGrid.moveToward(posX, posY, cibleX, cibleY);
                posX = next[0]; posY = next[1];
                if (posX == cibleX && posY == cibleY) {
                    etatCourant = Etat.ACTIF;
                    informerArrivee();
                }
                break;

            case ACTIF:
                // Consommer de l'eau pendant l'intervention
                reserveEau -= 10;
                System.out.println("[POMPIER] " + getLocalName()
                    + " — Intervention en cours. Eau restante: " + reserveEau + "%");

                if (reserveEau <= 0) {
                    // ABANDON
                    reserveEau = 0;
                    etatCourant = Etat.RETOUR;
                    envoyerABORT();
                }
                break;

            case RETOUR:
                int[] ret = UrbanGrid.moveToward(posX, posY, baseX, baseY);
                posX = ret[0]; posY = ret[1];
                if (posX == baseX && posY == baseY) {
                    // Recharger l'eau au retour
                    reserveEau = 100;
                    etatCourant = Etat.INACTIF;
                    System.out.println("[POMPIER] " + getLocalName()
                        + " de retour à la base. Eau rechargée.");
                }
                break;

            default:
                break;
        }
    }

    private void informerArrivee() {
        System.out.println("[POMPIER] " + getLocalName()
            + " arrivé sur les lieux (" + posX + "," + posY + ")");
        if (repartiteurAssignant != null) {
            ACLMessage info = new ACLMessage(ACLMessage.INFORM);
            info.addReceiver(repartiteurAssignant);
            info.setContent("ARRIVEE;" + posX + ";" + posY);
            info.setConversationId(convIdMission);
            info.setOntology("EmergencyOntology");
            send(info);
        }
        // Fin d'intervention après 6 ticks (6s) si pas d'abandon
        addBehaviour(new WakerBehaviour(this, 6000) {
            @Override
            protected void onWake() {
                if (etatCourant == Etat.ACTIF) terminerIntervention();
            }
        });
    }

    private void terminerIntervention() {
        System.out.println("[POMPIER] " + getLocalName() + " — Incident éteint / secouru.");
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

    private void envoyerABORT() {
        System.out.println("[POMPIER] " + getLocalName()
            + " — ABANDON : réserve d'eau épuisée !");
        if (repartiteurAssignant != null) {
            ACLMessage abort = new ACLMessage(ACLMessage.INFORM);
            abort.addReceiver(repartiteurAssignant);
            abort.setContent("ABORT;EAU_EPUISEE;" + posX + ";" + posY);
            abort.setConversationId(convIdMission);
            abort.setOntology("EmergencyOntology");
            send(abort);
        }
        cibleX = -1; cibleY = -1;
        repartiteurAssignant = null;
    }
    
    
    protected void onTick() {

    	// 1. Utilise les variables globales de ton agent (celles qui ont reçu les données du message ACL)

    	// Supprime les lignes "int destX;" etc., car elles cachent les vraies données.



    	// 2. Calcul du mouvement vers la destination de l'incident

    	int[] nextPos = UrbanGrid.moveToward(posX, posY, cibleX, cibleY); posX = nextPos[0];

    	posY = nextPos[1];



    	// 3. Log pour la console

    	System.out.println("[" + getLocalName() + "] En route... Position : (" + posX + "," + posY + ")");



    	// 4. Affichage de la carte (seulement pour Ambulance1 pour éviter de surcharger la console)

    	if (getLocalName().equals("Ambulance1")) {

    	// On passe les positions actuelles et les cibles à la méthode de dessin

    	UrbanGrid.dessinerVille(posX, posY, 0, 0, cibleX, cibleY, 0, 0); }

    	}
    

    // ─── Comportement : écoute CFP ───
    private class EcouteCFP extends CyclicBehaviour {
        private static final long serialVersionUID = 1L; // ← ajouter ici

        @Override
        public void action() {
            MessageTemplate mtCFP = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage cfp = myAgent.receive(mtCFP);
            if (cfp != null) {
                traiterCFP(cfp);
                return;
            }

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

        private void traiterCFP(ACLMessage cfp) {
            String contenu = cfp.getContent();
            if (contenu == null) return;
            String[] parts = contenu.split(";");
            if (parts.length < 5) return;

            String type = parts[0];

            // Pompier intervient pour FIRE et STRUCTURAL_COLLAPSE
            if (!"FIRE".equals(type) && !"STRUCTURAL_COLLAPSE".equals(type)) return;

            if (etatCourant != Etat.INACTIF || reserveEau < 20) {
                ACLMessage refus = cfp.createReply();
                refus.setPerformative(ACLMessage.REFUSE);
                refus.setContent(etatCourant != Etat.INACTIF ? "EN_COURS" : "EAU_INSUFFISANTE");
                send(refus);
                return;
            }

            int incX = Integer.parseInt(parts[2]);
            int incY = Integer.parseInt(parts[3]);
            double dist = UrbanGrid.distance(posX, posY, incX, incY);

            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.format(java.util.Locale.US, "INACTIF;%.2f;%d;%d", dist, posX, posY));            propose.setOntology("EmergencyOntology");
            send(propose);

            System.out.println("[POMPIER] " + myAgent.getLocalName()
                + " PROPOSE pour " + type + " — dist=" + String.format("%.2f", dist));
        }

        private void traiterReponse(ACLMessage msg) {
            if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                String contenu = msg.getContent();
                String[] parts = (contenu != null) ? contenu.split(";") : new String[0];

                if (parts.length >= 2) {
                    cibleX = Integer.parseInt(parts[0]);
                    cibleY = Integer.parseInt(parts[1]);
                } else {
                    cibleX = baseX; cibleY = baseY;
                }

                repartiteurAssignant = msg.getSender();
                convIdMission = msg.getConversationId();
                typeIncidentCourant = "";
                etatCourant = Etat.EN_DEPLACEMENT;

                System.out.println("[POMPIER] " + myAgent.getLocalName()
                    + " ACCEPTÉ → en route vers (" + cibleX + "," + cibleY + ")");
            } else {
                System.out.println("[POMPIER] " + myAgent.getLocalName() + " REJETÉ.");
            }
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[POMPIER] " + getLocalName() + " arrêté.");
    }
}
