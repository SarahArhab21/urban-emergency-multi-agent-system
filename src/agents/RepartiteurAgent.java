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

import java.util.*;

/**
 * MEMBRE A — Agent Répartiteur (Dispatcher)
 *
 * CORRECTION FINALE :
 *   - loggerEnvoyer() pour RESOLU utilise l'ID court "inc_xxxxx"
 *     comme convId (identique à ce qui a été stocké lors de la détection)
 *   - Idem pour ABORT
 */
public class RepartiteurAgent extends Agent {

    private static final double ALPHA       = 0.5;
    private static final double BETA        = 0.3;
    private static final double GAMMA       = 0.2;
    private static final long   TIMEOUT_CFP = 5000;

    private final Map<String, IncidentInfo> incidentsActifs = new HashMap<>();

    // Cache AID du Logger
    private AID loggerAID = null;

    @Override
    protected void setup() {
        System.out.println("[REPARTITEUR] Démarré — FIPA Contract Net activé.");
        
        // 1. Enregistrement dans les Pages Jaunes (DF)
        enregistrerDF();

        // 2. Création du conteneur pour gérer les tâches en parallèle
        // Le flag WHEN_ALL permet de garder les deux comportements actifs en continu
        ParallelBehaviour gestionMultiTaches = new ParallelBehaviour(this, ParallelBehaviour.WHEN_ALL);

        // 3. Ajout des comportements au conteneur parallèle
        gestionMultiTaches.addSubBehaviour(new EcouteIncidents());
        gestionMultiTaches.addSubBehaviour(new EcouteRetours());

        // 4. On ajoute le conteneur global à l'agent
        addBehaviour(gestionMultiTaches);
    }

    // =========================================================
    //  DF
    // =========================================================

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Repartiteur");
        sd.setName("Groupe-Coordination");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("[REPARTITEUR] Enregistré dans le DF (Repartiteur).");
        } catch (FIPAException e) {
            System.err.println("[REPARTITEUR] Erreur DF : " + e.getMessage());
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
            if (r.length > 0) {
                loggerAID = r[0].getName();
                System.out.println("[REPARTITEUR] Logger trouvé : " + loggerAID.getLocalName());
            }
        } catch (FIPAException e) {
            System.err.println("[REPARTITEUR] Logger introuvable : " + e.getMessage());
        }
        return loggerAID;
    }

    /**
     * Envoie un INFORM au Logger.
     *
     * @param contenu  texte de l'événement
     * @param convId   DOIT être l'ID court de l'incident : "inc_xxxxx"
     *                 (identique à la clé stockée dans heureDetection du Logger)
     */
    private void loggerEnvoyer(String contenu, String convId) {
        AID logger = obtenirLogger();
        if (logger == null) return;
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(logger);
        msg.setContent(contenu);
        msg.setConversationId(convId);   // toujours "inc_xxxxx" — jamais "incident-inc_xxxxx"
        msg.setOntology("LogOntology");
        send(msg);
    }

    /**
     * Extrait l'ID court "inc_xxxxx" depuis un convId quelconque.
     * Exemples :
     *   "incident-inc_7a193521"        → "inc_7a193521"
     *   "incident-inc_7a193521_cfp"    → "inc_7a193521"
     *   "inc_7a193521"                 → "inc_7a193521"
     */
    private static String extraireIdCourt(String convId) {
        if (convId == null) return null;
        int idx = convId.indexOf("inc_");
        if (idx < 0) return convId;                      // pas de "inc_" → retourner tel quel
        // Couper à la fin de l'ID (arrêter au premier '_' après le UUID ou à la fin)
        String apres = convId.substring(idx);            // "inc_7a193521" ou "inc_7a193521_cfp"
        // L'ID fait toujours 12 caractères : "inc_" (4) + 8 hex = 12
        // On s'arrête au premier caractère non alphanumérique/underscore APRÈS les 12 premiers
        // En pratique : split sur "_reaffect", "_cfp", "_trafic"
        String[] parts = apres.split("_(cfp|reaffect|trafic)");
        return parts[0];   // "inc_7a193521"
    }

    // =========================================================
    //  Comportement 1 : Écoute des incidents (INFORM capteurs)
    // =========================================================

    private class EcouteIncidents extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology("EmergencyOntology")
            );
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String contenu = msg.getContent();
                if (contenu != null
                        && !contenu.startsWith("RESOLU")
                        && !contenu.startsWith("ABORT")
                        && !contenu.startsWith("ARRIVEE")
                        && !contenu.startsWith("SATURATION")
                        && !contenu.startsWith("PERIMETRE")
                        && !contenu.startsWith("CONFINEMENT")) {
                    lancerContractNet(contenu, msg.getConversationId());
                }
            } else {
                block();
            }
        }
    }

    // =========================================================
    //  Comportement 2 : Écoute des retours (RESOLU, ABORT)
    // =========================================================

    private class EcouteRetours extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String contenu = msg.getContent();
                String convId  = msg.getConversationId();
                if (contenu == null) return;

                if (contenu.startsWith("RESOLU")) {
                    System.out.println("[REPARTITEUR] ✅ Incident RÉSOLU : " + convId);
                    incidentsActifs.remove(convId);

                    // ── COPIE AU LOGGER avec l'ID COURT ──
                    String idCourt = extraireIdCourt(convId);
                    loggerEnvoyer("RESOLU;" + idCourt, idCourt);

                } else if (contenu.startsWith("ABORT")) {
                    System.out.println("[REPARTITEUR] ⚠️ ABANDON reçu pour : " + convId);

                    // ── COPIE AU LOGGER avec l'ID COURT ──
                    String idCourt = extraireIdCourt(convId);
                    loggerEnvoyer("ABORT;" + idCourt + ";" + contenu, idCourt);

                    IncidentInfo info = incidentsActifs.get(convId);
                    if (info != null) {
                        System.out.println("[REPARTITEUR] Réaffectation de : " + info.contenu);
                        lancerContractNet(info.contenu, convId + "_reaffect");
                        incidentsActifs.remove(convId);
                    }
                }
            } else {
                block();
            }
        }
    }

    // =========================================================
    //  FIPA Contract Net
    // =========================================================

    private void lancerContractNet(final String contenuIncident, final String convId) {
        String[] parts = contenuIncident.split(";");
        if (parts.length < 5) return;

        final String type    = parts[0];
        final String gravite = parts[1];
        final int    incX    = Integer.parseInt(parts[2]);
        final int    incY    = Integer.parseInt(parts[3]);
        final String id      = parts[4].trim();   // "inc_xxxxx" — ID COURT

        System.out.println("[REPARTITEUR] 🚨 Incident reçu : " + type
            + " [" + gravite + "] @ (" + incX + "," + incY + ") ID=" + id);

        incidentsActifs.put(convId, new IncidentInfo(contenuIncident, type, incX, incY));

        // ── COPIE AU LOGGER — convId = id court "inc_xxxxx" ──
        loggerEnvoyer(contenuIncident, id);

        demanderCorridor(0, 0, incX, incY, convId);

        List<AID> unites = chercherUnites(type);
        if (unites.isEmpty()) {
            System.err.println("[REPARTITEUR] ❌ Aucune unité disponible pour : " + type);
            return;
        }

        final String cfpConvId = convId + "_cfp";
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        for (AID u : unites) cfp.addReceiver(u);
        cfp.setContent(contenuIncident);
        cfp.setConversationId(cfpConvId);
        cfp.setOntology("EmergencyOntology");
        cfp.setProtocol("fipa-contract-net");
        cfp.setReplyByDate(new Date(System.currentTimeMillis() + TIMEOUT_CFP));
        send(cfp);

        System.out.println("[REPARTITEUR] CFP envoyé à " + unites.size()
            + " unités pour " + type);

        final int    nbUnites     = unites.size();
        final String typeIncident = type;
        final int    finalIncX    = incX;
        final int    finalIncY    = incY;

        addBehaviour(new WakerBehaviour(this, TIMEOUT_CFP) {
            @Override
            protected void onWake() {
                collecterEtChoisir(cfpConvId, convId, typeIncident,
                    finalIncX, finalIncY, nbUnites);
            }
        });
    }

    // =========================================================
    //  Collecte des PROPOSE + fonction d'utilité
    // =========================================================

    private void collecterEtChoisir(String cfpConvId, String incidentConvId,
                                     String type, int incX, int incY, int nbMax) {
        List<OffreCandidat> offres = new ArrayList<>();

        MessageTemplate mt = MessageTemplate.and(
            MessageTemplate.MatchConversationId(cfpConvId),
            MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                MessageTemplate.MatchPerformative(ACLMessage.REFUSE)
            )
        );

        ACLMessage msg;
        while ((msg = receive(mt)) != null) {
            if (msg.getPerformative() == ACLMessage.PROPOSE) {
                double utilite = calculerUtilite(msg, type, incX, incY);
                offres.add(new OffreCandidat(msg, utilite));
                System.out.println("[REPARTITEUR] PROPOSE de "
                    + msg.getSender().getLocalName()
                    + " — U=" + String.format("%.3f", utilite));
            } else {
                System.out.println("[REPARTITEUR] REFUSE de "
                    + msg.getSender().getLocalName());
            }
        }

        if (offres.isEmpty()) {
            System.err.println("[REPARTITEUR] ⚠️ Aucune proposition reçue pour convId="
                + incidentConvId);
            return;
        }

        offres.sort((a, b) -> Double.compare(b.utilite, a.utilite));
        OffreCandidat gagnant = offres.get(0);

        ACLMessage accept = gagnant.msg.createReply();
        accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
        accept.setContent(incX + ";" + incY);
        accept.setConversationId(incidentConvId);
        accept.setOntology("EmergencyOntology");
        send(accept);

        System.out.println("[REPARTITEUR] ✅ ACCEPT → "
            + gagnant.msg.getSender().getLocalName()
            + " (U=" + String.format("%.3f", gagnant.utilite) + ")");

        for (int i = 1; i < offres.size(); i++) {
            ACLMessage reject = offres.get(i).msg.createReply();
            reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
            reject.setContent("MEILLEURE_OFFRE_CHOISIE");
            reject.setOntology("EmergencyOntology");
            send(reject);
            System.out.println("[REPARTITEUR] REJECT → "
                + offres.get(i).msg.getSender().getLocalName());
        }
    }

    // =========================================================
    //  Fonction d'utilité
    // =========================================================

    private double calculerUtilite(ACLMessage propose, String typeIncident,
                                    int incX, int incY) {
        String contenu = propose.getContent();
        if (contenu == null) return 0.0;
        String[] parts = contenu.split(";");
        if (parts.length < 4) return 0.0;

        String statut   = parts[0];
        double distance = Double.parseDouble(parts[1]);

        double distMax       = Math.sqrt(UrbanGrid.WIDTH * UrbanGrid.WIDTH
            + UrbanGrid.HEIGHT * UrbanGrid.HEIGHT);
        double scoreDistance = (distance > 0)
            ? (distMax - distance) / distMax : 1.0;

        double scoreCharge;
        switch (statut.toUpperCase()) {
            case "INACTIF":    scoreCharge = 1.0; break;
            case "PATROUILLE": scoreCharge = 0.8; break;
            default:           scoreCharge = 0.2; break;
        }

        return ALPHA * scoreDistance + BETA * 1.0 + GAMMA * scoreCharge;
    }

    // =========================================================
    //  DF — chercher unités
    // =========================================================

    private List<AID> chercherUnites(String type) {
        List<AID> aides = new ArrayList<>();
        for (String capacite : obtenirCapacites(type)) {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(capacite);
            template.addServices(sd);
            try {
                DFAgentDescription[] resultats = DFService.search(this, template);
                for (DFAgentDescription r : resultats) {
                    if (!aides.contains(r.getName())) aides.add(r.getName());
                }
            } catch (FIPAException e) {
                System.err.println("[REPARTITEUR] Erreur DF pour " + capacite
                    + " : " + e.getMessage());
            }
        }
        return aides;
    }

    private String[] obtenirCapacites(String type) {
        switch (type) {
            case "FIRE":                
                // On cherche les pompiers ET la police pour sécuriser le périmètre
                return new String[]{"FIRE", "PERIMETER"}; 
            case "MEDICAL":             
                return new String[]{"MEDICAL"};
            case "STRUCTURAL_COLLAPSE": 
                return new String[]{"RESCUE", "CROWD_CONTROL"};
            case "BIOHAZARD":
            case "CRYOGENIC_LEAK":      
                return new String[]{"BIOHAZARD_CONTAINMENT"};
            default:                    
                return new String[]{type};
        }
    }

    // =========================================================
    //  Corridor de trafic
    // =========================================================

    private void demanderCorridor(int startX, int startY, int endX, int endY, String convId) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("ControleurTrafic");
        template.addServices(sd);
        try {
            DFAgentDescription[] resultats = DFService.search(this, template);
            if (resultats.length == 0) return;
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(resultats[0].getName());
            req.setContent("CORRIDOR;" + startX + ";" + startY + ";"
                + endX + ";" + endY);
            req.setConversationId(convId + "_trafic");
            req.setOntology("EmergencyOntology");
            send(req);
            System.out.println("[REPARTITEUR] Corridor demandé vers ("
                + endX + "," + endY + ")");
        } catch (FIPAException e) {
            System.err.println("[REPARTITEUR] Erreur Trafic : " + e.getMessage());
        }
    }

    // =========================================================
    //  Classes internes
    // =========================================================

    private static class IncidentInfo {
        final String contenu, type;
        final int    x, y;
        IncidentInfo(String contenu, String type, int x, int y) {
            this.contenu = contenu; this.type = type;
            this.x = x; this.y = y;
        }
    }

    private static class OffreCandidat {
        final ACLMessage msg;
        final double     utilite;
        OffreCandidat(ACLMessage msg, double utilite) {
            this.msg = msg; this.utilite = utilite;
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        System.out.println("[REPARTITEUR] Arrêté.");
    }
}