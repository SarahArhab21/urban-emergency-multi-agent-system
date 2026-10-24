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

public class PoliceAgent extends Agent {

    private static final long serialVersionUID = 1L;

    public enum Etat { PATROUILLE, EN_DEPLACEMENT, SUR_LIEUX, RETOUR }

    private Etat   etatCourant = Etat.PATROUILLE;
    private int    posX, posY;
    private int    baseX, baseY;
    private int    cibleX = -1, cibleY = -1;

    private int[][] pointsPatrouille;
    private int     indexPatrouille = 0;

    private AID    repartiteurAssignant;
    private String convIdMission;

    private AID loggerAID = null;

    @Override
    protected void setup() {
        int[] pos = UrbanGrid.randomPosition();
        posX = pos[0]; posY = pos[1];
        baseX = posX;  baseY = posY;

        pointsPatrouille = new int[][]{
            {posX, posY},
            {(posX + 5) % UrbanGrid.WIDTH,  posY},
            {(posX + 5) % UrbanGrid.WIDTH,  (posY + 5) % UrbanGrid.HEIGHT},
            {posX,                           (posY + 5) % UrbanGrid.HEIGHT}
        };

        System.out.println("[POLICE] " + getLocalName()
            + " démarrée en (" + posX + "," + posY + ") — patrouille active.");

        enregistrerDF();
        addBehaviour(new EcouteCFP());

        addBehaviour(new TickerBehaviour(this, 1500) {
            private static final long serialVersionUID = 1L;
            @Override
            protected void onTick() {
                deplacer();
            }
        });
    }

    // =========================================================
    //  DF
    // =========================================================

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType("CROWD_CONTROL");
        sd1.setName("Groupe-Urgence");
        dfd.addServices(sd1);

        ServiceDescription sd2 = new ServiceDescription();
        sd2.setType("PERIMETER");
        sd2.setName("Groupe-Urgence");
        dfd.addServices(sd2);

        ServiceDescription sd3 = new ServiceDescription();
        sd3.setType("STRUCTURAL_COLLAPSE");
        sd3.setName("Groupe-Urgence");
        dfd.addServices(sd3);

        ServiceDescription sd4 = new ServiceDescription();
        sd4.setType("FIRE");
        sd4.setName("Groupe-Urgence");
        dfd.addServices(sd4);

        try {
            DFService.register(this, dfd);
            System.out.println("[POLICE] " + getLocalName()
                + " enregistrée dans DF (CROWD_CONTROL + PERIMETER + FIRE + STRUCTURAL_COLLAPSE).");
        } catch (FIPAException e) {
            System.err.println("[POLICE] Erreur DF : " + e.getMessage());
        }
    }

    // =========================================================
    //  Logger
    // =========================================================

    private AID obtenirLogger() {
        if (loggerAID != null) return loggerAID;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Logger");
        template.addServices(sd);
        try {
            DFAgentDescription[] r = DFService.search(this, template);
            if (r.length > 0) loggerAID = r[0].getName();
        } catch (FIPAException ignored) {}
        return loggerAID;
    }

    private void loggerEnvoyer(String contenu, String convId) {
        AID logger = obtenirLogger();
        if (logger == null) return;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(logger);
        msg.setContent(contenu);
        msg.setConversationId(convId);
        msg.setOntology("LogOntology");
        send(msg);
    }

    // =========================================================
    //  Déplacement (FSM)
    // =========================================================

    private void deplacer() {
        switch (etatCourant) {

            case PATROUILLE:
                int[] pt   = pointsPatrouille[indexPatrouille];
                int[] next = UrbanGrid.moveToward(posX, posY, pt[0], pt[1]);
                posX = next[0]; posY = next[1];
                if (posX == pt[0] && posY == pt[1]) {
                    indexPatrouille = (indexPatrouille + 1) % pointsPatrouille.length;
                }
                break;

            case EN_DEPLACEMENT:
                if (cibleX >= 0) {
                    int[] n = UrbanGrid.moveToward(posX, posY, cibleX, cibleY);
                    posX = n[0]; posY = n[1];
                    if (posX == cibleX && posY == cibleY) {
                        etatCourant = Etat.SUR_LIEUX;
                        informerArrivee();
                    }
                }
                break;

            case RETOUR:
                int[] r = UrbanGrid.moveToward(posX, posY, baseX, baseY);
                posX = r[0]; posY = r[1];
                if (posX == baseX && posY == baseY) {
                    etatCourant = Etat.PATROUILLE;
                    System.out.println("[POLICE] " + getLocalName()
                        + " de retour — reprise de la patrouille.");
                }
                break;

            default:
                break;
        }
    }

    // =========================================================
    //  Arrivée sur les lieux
    // =========================================================

    private void informerArrivee() {
        System.out.println("[POLICE] " + getLocalName()
            + " sur les lieux — sécurisation du périmètre.");

        if (repartiteurAssignant != null) {
            ACLMessage info = new ACLMessage(ACLMessage.INFORM);
            info.addReceiver(repartiteurAssignant);
            info.setContent("PERIMETRE_SECURISE;" + posX + ";" + posY);
            info.setConversationId(convIdMission);
            info.setOntology("EmergencyOntology");
            send(info);
        }

        addBehaviour(new WakerBehaviour(this, 4000) {
            private static final long serialVersionUID = 1L;
            @Override
            protected void onWake() {
                terminerIntervention();
            }
        });
    }

    // =========================================================
    //  Fin d'intervention
    // =========================================================

    private void terminerIntervention() {
        System.out.println("[POLICE] " + getLocalName() + " — Périmètre levé.");

        if (repartiteurAssignant != null) {
            ACLMessage info = new ACLMessage(ACLMessage.INFORM);
            info.addReceiver(repartiteurAssignant);
            info.setContent("RESOLU;" + posX + ";" + posY);
            info.setConversationId(convIdMission);
            info.setOntology("EmergencyOntology");
            send(info);

            // Copie au Logger avec l'ID court
            loggerEnvoyer("RESOLU;" + convIdMission, convIdMission);
        }

        etatCourant          = Etat.RETOUR;
        cibleX               = -1;
        cibleY               = -1;
        repartiteurAssignant = null;
    }

    // =========================================================
    //  Comportement : écoute CFP + ACCEPT/REJECT
    // =========================================================

    private class EcouteCFP extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;

        @Override
        public void action() {
            ACLMessage cfp = myAgent.receive(
                MessageTemplate.MatchPerformative(ACLMessage.CFP));
            if (cfp != null) {
                traiterCFP(cfp);
                return;
            }

            ACLMessage rep = myAgent.receive(MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
            ));
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

            boolean competent = "FIRE".equals(type)
                || "STRUCTURAL_COLLAPSE".equals(type)
                || "CROWD_CONTROL".equals(type);

            if (!competent) return;   // refus silencieux

            if (etatCourant == Etat.SUR_LIEUX || etatCourant == Etat.EN_DEPLACEMENT) {
                ACLMessage refus = cfp.createReply();
                refus.setPerformative(ACLMessage.REFUSE);
                refus.setContent("EN_INTERVENTION");
                send(refus);
                return;
            }

            int incX = Integer.parseInt(parts[2]);
            int incY = Integer.parseInt(parts[3]);
            double dist = UrbanGrid.distance(posX, posY, incX, incY);

            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.format(java.util.Locale.US,
                "%s;%.2f;%d;%d", etatCourant.name(), dist, posX, posY));
            propose.setOntology("EmergencyOntology");
            send(propose);

            System.out.println("[POLICE] " + myAgent.getLocalName()
                + " PROPOSE pour " + type
                + " — dist=" + String.format("%.2f", dist));
        }

        private void traiterReponse(ACLMessage msg) {
            if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                String contenu = msg.getContent();
                String[] parts = (contenu != null) ? contenu.split(";") : new String[0];

                cibleX = (parts.length >= 2) ? Integer.parseInt(parts[0]) : baseX;
                cibleY = (parts.length >= 2) ? Integer.parseInt(parts[1]) : baseY;

                repartiteurAssignant = msg.getSender();
                convIdMission        = msg.getConversationId();
                etatCourant          = Etat.EN_DEPLACEMENT;

                System.out.println("[POLICE] " + myAgent.getLocalName()
                    + " ACCEPTÉ → en route vers (" + cibleX + "," + cibleY + ")");
            } else {
                System.out.println("[POLICE] " + myAgent.getLocalName() + " REJETÉ.");
            }
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[POLICE] " + getLocalName() + " arrêtée.");
    }
}