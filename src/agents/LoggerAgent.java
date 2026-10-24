package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MEMBRE B — Agent Enregistreur d'Incidents (Logger)
 *
 * Reçoit des copies envoyées par le RepartiteurAgent via l'ontologie "LogOntology".
 * Format attendu :
 *   - NOUVEL INCIDENT  : "TYPE;GRAVITE;X;Y;inc_xxxxx"   convId = "inc_xxxxx"
 *   - RÉSOLU           : "RESOLU;convId"                 convId = "inc_xxxxx"
 *   - ABANDON          : "ABORT;convId;..."              convId = "inc_xxxxx"
 */
public class LoggerAgent extends Agent {

    private PrintWriter logWriter;

    // Statistiques
    private int  totalIncidents    = 0;
    private int  incidentsResolus  = 0;
    private int  abandons          = 0;
    private long sommeTempsReponse = 0;

    private final Map<String, Long>   heureDetection  = new HashMap<>();
    private final Map<String, Long>   heureResolution = new HashMap<>();
    private final Map<String, String> typeIncident    = new HashMap<>();
    private final Set<String>         abandonsVus     = new HashSet<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    // =========================================================
    //  Cycle de vie
    // =========================================================

    @Override
    protected void setup() {
        System.out.println("[LOGGER] Démarré — surveillance globale.");
        ouvrirFichierLog();
        enregistrerDF();

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    enregistrerMessage(msg);
                } else {
                    block();
                }
            }
        });

        // Rapport final dans 120 secondes (mettre 30000 pour les tests rapides)
        addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 120_000) {
            @Override
            protected void onWake() {
                genererRapportFinal();
            }
        });
    }

    @Override
    protected void takeDown() {
        genererRapportFinal();
        if (logWriter != null) {
            ecrire("========== SESSION TERMINÉE : " + new Date() + " ==========");
            logWriter.close();
        }
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
    }

    // =========================================================
    //  DF
    // =========================================================

    private void enregistrerDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Logger");
        sd.setName("Groupe-Audit");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("[LOGGER] Erreur DF : " + e.getMessage());
        }
    }

    // =========================================================
    //  Fichier log
    // =========================================================

    private void ouvrirFichierLog() {
        try {
            logWriter = new PrintWriter(new FileWriter("log_urgences.txt", true), true);
            ecrire("========== SESSION DÉMARRÉE : " + new Date() + " ==========");
        } catch (IOException e) {
            System.err.println("[LOGGER] Impossible d'ouvrir log_urgences.txt : " + e.getMessage());
        }
    }

    private void ecrire(String ligne) {
        if (logWriter != null) logWriter.println(ligne);
    }

    // =========================================================
    //  Traitement des messages
    // =========================================================

    private void enregistrerMessage(ACLMessage msg) {
        String horodatage   = sdf.format(new Date());
        String expediteur   = msg.getSender().getLocalName();
        String performative = ACLMessage.getPerformative(msg.getPerformative());
        String contenu      = msg.getContent();
        String convId       = msg.getConversationId();
        String ontologie    = msg.getOntology() != null ? msg.getOntology() : "";

        String ligne = String.format("[%s] %s | FROM: %-20s | CONV: %-25s | %s",
            horodatage, performative, expediteur,
            convId != null ? convId : "(null)",
            contenu != null ? contenu : "(vide)");

        ecrire(ligne);
        System.out.println("[LOGGER] " + ligne);

        // Traitement sémantique uniquement pour les messages du Répartiteur
        if ("LogOntology".equals(ontologie)) {
            traiterMessageLog(contenu, convId);
        }
    }

    /**
     * Analyse les messages envoyés par le Répartiteur via LogOntology.
     *
     * Formats :
     *   TYPE;GRAVITE;X;Y;inc_xxxxx   → nouvel incident
     *   RESOLU;convId                → incident résolu
     *   ABORT;convId;...             → abandon
     */
    private void traiterMessageLog(String contenu, String convId) {
        if (contenu == null) return;
        String contenuUp = contenu.toUpperCase();

        // ── 1. RÉSOLUTION ──────────────────────────────────────────────────
        if (contenuUp.startsWith("RESOLU")) {
            String id = extraireId(convId, contenu);
            if (id != null && heureDetection.containsKey(id)) {
                incidentsResolus++;
                long duree = System.currentTimeMillis() - heureDetection.get(id);
                sommeTempsReponse += duree;
                heureResolution.put(id, System.currentTimeMillis());
                ecrire(">>> RÉSOLU #" + id + " en " + (duree / 1000) + "s");
            } else {
                ecrire(">>> RÉSOLU (id inconnu) convId=" + convId);
                incidentsResolus++;
            }
            return;
        }

        // ── 2. ABANDON ─────────────────────────────────────────────────────
        if (contenuUp.startsWith("ABORT")) {
            String cle = (convId != null && !convId.isEmpty()) ? convId : contenu;
            if (abandonsVus.add(cle)) {
                abandons++;
                ecrire(">>> ABANDON : " + contenu);
            }
            return;
        }

        // ── 3. NOUVEL INCIDENT ─────────────────────────────────────────────
        // Format attendu : TYPE;GRAVITE;X;Y;inc_xxxxx
        String[] parts = contenu.split(";");
        if (parts.length >= 5) {
            totalIncidents++;
            String id = parts[4].trim();
            heureDetection.put(id, System.currentTimeMillis());
            typeIncident.put(id, parts[0].trim());

            ecrire(">>> NOUVEL INCIDENT #" + id
                + " | Type="    + parts[0].trim()
                + " | Gravité=" + parts[1].trim()
                + " | Pos=("    + parts[2].trim() + "," + parts[3].trim() + ")");

        } else if (parts.length >= 4) {
            totalIncidents++;
            String id = (convId != null) ? convId : "inc_inconnu_" + totalIncidents;
            heureDetection.put(id, System.currentTimeMillis());
            typeIncident.put(id, parts[0].trim());

            ecrire(">>> NOUVEL INCIDENT #" + id
                + " | Type="    + parts[0].trim()
                + " | Gravité=" + parts[1].trim()
                + " | Pos=("    + parts[2].trim() + "," + parts[3].trim() + ")");
        }
    }

    // =========================================================
    //  Extraction de l'ID
    // =========================================================

    private String extraireId(String convId, String contenu) {
        if (contenu != null) {
            for (String token : contenu.split("[;:\\s]+")) {
                token = token.trim();
                if (heureDetection.containsKey(token)) return token;
            }
        }
        if (convId != null) {
            if (heureDetection.containsKey(convId)) return convId;
            int idx = convId.indexOf("inc_");
            if (idx >= 0) {
                String candidate = convId.substring(idx).split("[^a-z0-9_]")[0];
                if (heureDetection.containsKey(candidate)) return candidate;
            }
        }
        return null;
    }

    // =========================================================
    //  Rapport final
    // =========================================================

    private void genererRapportFinal() {
        ecrire("\n========== RAPPORT FINAL ==========");
        ecrire("Total incidents détectés  : " + totalIncidents);
        ecrire("Incidents résolus         : " + incidentsResolus);
        ecrire("Incidents non résolus     : " + (totalIncidents - incidentsResolus));
        ecrire("Abandons enregistrés      : " + abandons);

        long moyenneS = 0;
        if (incidentsResolus > 0) {
            moyenneS = (sommeTempsReponse / incidentsResolus) / 1000;
            ecrire("Temps de réponse moyen   : " + moyenneS + "s");
        } else {
            ecrire("Temps de réponse moyen   : N/A");
        }

        double tauxSucces = totalIncidents > 0
            ? (100.0 * incidentsResolus / totalIncidents) : 0.0;
        ecrire(String.format("Taux de succès           : %.1f%%", tauxSucces));
        ecrire("====================================\n");

        try (PrintWriter rw = new PrintWriter("rapport_final.txt")) {
            rw.println("RAPPORT FINAL SRUU - " + new Date());
            rw.println("Incidents détectés  : " + totalIncidents);
            rw.println("Résolus             : " + incidentsResolus);
            rw.println("Non résolus         : " + (totalIncidents - incidentsResolus));
            rw.println("Abandons            : " + abandons);
            if (incidentsResolus > 0)
                rw.println("Temps moyen (s)     : " + moyenneS);
            rw.printf("Taux succès (%%)     : %.1f%n", tauxSucces);
        } catch (IOException e) {
            System.err.println("[LOGGER] Erreur rapport_final : " + e.getMessage());
        }

        System.out.println("[LOGGER] Rapport final généré.");
    }
}