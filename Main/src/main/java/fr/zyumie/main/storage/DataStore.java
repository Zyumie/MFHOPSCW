package fr.zyumie.main.storage;

import java.util.Map;
import java.util.UUID;

/**
 * Abstraction de stockage utilisée par TOUS les modules (Faction, Profile, Home, ...).
 * Permet de basculer YAML <-> MySQL uniquement via config.yml du Main, sans toucher
 * au code des modules.
 *
 * Convention: chaque module a son propre "namespace" (ex: "faction", "home", "oxygen")
 * pour éviter les collisions de clés.
 */
public interface DataStore {

    /**
     * Sauvegarde une donnée brute (sera sérialisée en YAML map ou en JSON column selon l'implémentation).
     * @param namespace ex: "faction"
     * @param key ex: UUID joueur ou nom de faction
     * @param data map de données à stocker
     */
    void save(String namespace, String key, Map<String, Object> data);

    /**
     * Charge une donnée. Retourne null si absente.
     */
    Map<String, Object> load(String namespace, String key);

    /**
     * Supprime une entrée.
     */
    void delete(String namespace, String key);

    /**
     * Liste toutes les clés d'un namespace (ex: tous les UUID ayant un profil).
     */
    Iterable<String> listKeys(String namespace);

    boolean exists(String namespace, String key);

    /** Helpers pratiques pour les clés joueurs */
    default void save(String namespace, UUID uuid, Map<String, Object> data) {
        save(namespace, uuid.toString(), data);
    }

    default Map<String, Object> load(String namespace, UUID uuid) {
        return load(namespace, uuid.toString());
    }

    /** Ferme les connexions (appelé à l'arrêt du serveur) */
    void close();
}
