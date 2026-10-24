package agents;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  SRUU — Système de Réponse aux Urgences Urbaines
 *  Lanceur JADE — Instructions de démarrage
 * ══════════════════════════════════════════════════════════════════════════
 *
 * STRUCTURE DU PROJET :
 * ─────────────────────
 *  src/agents/
 *  ├── UrbanGrid.java              (Membre B — Utilitaire)
 *  ├── CapteurAgent.java           (Membre B)
 *  ├── LoggerAgent.java            (Membre B)
 *  ├── RepartiteurAgent.java       (Membre A) ← Cœur du système
 *  ├── CoordinateurMedicalAgent.java (Membre A)
 *  ├── ControleurTraficAgent.java  (Membre A)
 *  ├── AmbulanceAgent.java         (Membre C)
 *  ├── PompierAgent.java           (Membre C)
 *  ├── PoliceAgent.java            (Membre C)
 *  └── BCUAgent.java               (Membre C — Biohazard)
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  COMMANDE DE LANCEMENT (dans le répertoire où se trouve jade.jar)
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Compiler d'abord :
 *    javac -cp jade.jar -d out src/agents/*.java
 *
 *  Lancer tous les agents (une seule commande) :
 *
 *    java -cp jade.jar:out jade.Boot \
 *      -gui \
 *      Logger:agents.LoggerAgent \
 *      Repartiteur:agents.RepartiteurAgent \
 *      CoordMedical:agents.CoordinateurMedicalAgent \
 *      Trafic:agents.ControleurTraficAgent \
 *      Ambulance1:agents.AmbulanceAgent \
 *      Ambulance2:agents.AmbulanceAgent \
 *      Pompier1:agents.PompierAgent \
 *      Pompier2:agents.PompierAgent \
 *      Police1:agents.PoliceAgent \
 *      BCU1:agents.BCUAgent \
 *      Capteur1:agents.CapteurAgent(5,5,FIRE) \
 *      Capteur2:agents.CapteurAgent(10,15,MEDICAL) \
 *      Capteur3:agents.CapteurAgent(3,18,STRUCTURAL_COLLAPSE) \
 *      Capteur4:agents.CapteurAgent(17,2,BIOHAZARD)
 *
 *  Sur Windows, remplacer ':' par ';' dans le classpath :
 *    java -cp jade.jar;out jade.Boot ...
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  SCÉNARIOS DE DÉMONSTRATION
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  SCÉNARIO 1 — Deux incidents simultanés (FIRE + MEDICAL)
 *  ─────────────────────────────────────────────────────────
 *  Les capteurs 1 et 2 génèrent automatiquement des incidents.
 *  Observez dans les logs :
 *    [REPARTITEUR] 🚨 Incident reçu : FIRE  ...
 *    [REPARTITEUR] 🚨 Incident reçu : MEDICAL ...
 *    [POMPIER] PROPOSE pour FIRE ...
 *    [AMBULANCE] PROPOSE pour MEDICAL ...
 *    [REPARTITEUR] ✅ ACCEPT → Pompier1
 *    [REPARTITEUR] ✅ ACCEPT → Ambulance1
 *
 *  SCÉNARIO 2 — Conflit de ressources (réaffectation)
 *  ─────────────────────────────────────────────────────
 *  Le Pompier1 sera accepté sur FIRE.
 *  Quand sa réserve d'eau tombe à 0 → ABORT envoyé.
 *  Le Répartiteur déclenche automatiquement un nouveau Contract Net.
 *  Observez :
 *    [POMPIER] — ABANDON : réserve d'eau épuisée !
 *    [REPARTITEUR] ⚠️ ABANDON reçu ...
 *    [REPARTITEUR] Réaffectation de : FIRE;HIGH;...
 *
 *  SCÉNARIO 3 — Rapport du Logger
 *  ─────────────────────────────────────────────────────
 *  Après ~2 minutes, consulter :
 *    cat log_urgences.txt
 *    cat rapport_final.txt
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  PROTOCOLE FIPA CONTRACT NET — Rappel
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Capteur ──INFORM──► Répartiteur
 *  Répartiteur ──CFP──► [Ambulance, Pompier, Police, BCU]
 *  Unités ──PROPOSE / REFUSE──► Répartiteur
 *  Répartiteur applique : U = 0.5*(1/dist) + 0.3*typeMatch + 0.2*charge
 *  Répartiteur ──ACCEPT_PROPOSAL──► Gagnant
 *  Répartiteur ──REJECT_PROPOSAL──► Autres
 *  Unité gagnante ──INFORM(RESOLU/ABORT)──► Répartiteur
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  FORMAT DES MESSAGES (contrat entre membres)
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Capteur → Répartiteur (INFORM) :
 *    "TYPE;GRAVITE;X;Y;ID_UNIQUE"
 *    Ex : "FIRE;HIGH;5;5;inc_a1b2c3d4"
 *
 *  Répartiteur → Unités (CFP) :
 *    Même format que ci-dessus
 *
 *  Unité → Répartiteur (PROPOSE) :
 *    "STATUT;DISTANCE;posX;posY"
 *    Ex : "INACTIF;3.61;2;3"
 *
 *  Répartiteur → Unité gagnante (ACCEPT_PROPOSAL) :
 *    "incidentX;incidentY"
 *    Ex : "5;5"
 *
 *  Unité → Répartiteur (INFORM résultat) :
 *    "RESOLU;posX;posY"    ou
 *    "ABORT;RAISON;posX;posY"
 *    Ex : "ABORT;EAU_EPUISEE;8;12"
 *
 */
public class SRUULauncher {
    // Cette classe sert uniquement de documentation.
    // Le lancement se fait via jade.Boot (voir commande ci-dessus).
    public static void main(String[] args) {
        System.out.println("Voir les commentaires Javadoc pour la commande de lancement JADE.");
    }
}
