package librarianinsight;

import java.util.UUID;

/** A retained client-side best-known link between a lectern and a librarian. */
public record LecternAssociation(UUID villagerUuid, Confidence confidence) {
    public enum Confidence {
        /** The legacy nearest-within-2.5-blocks rule; inherently estimated. */
        FALLBACK("Estimated (nearby librarian)", 0),
        /** Status event from an existing librarian correlated with one recent placement. */
        CLAIM_CORRELATED("Correlated recent claim", 1),
        /** A unique librarian work sound and unique workstation candidate agreed. */
        WORK_OBSERVED("Observed at this workstation", 2),
        /** Event 14, a profession transition, and one claim candidate agreed. */
        CLAIM_OBSERVED("Observed claim", 3),
        /** Authoritative JOB_SITE memory read on the integrated-server thread. */
        SERVER_CONFIRMED("Confirmed by integrated server", 4);

        private final String description;
        private final int strength;

        Confidence(String description, int strength) {
            this.description = description;
            this.strength = strength;
        }

        public String description() {
            return description;
        }

        boolean atLeast(Confidence other) {
            return strength >= other.strength;
        }

        boolean strongerThan(Confidence other) {
            return strength > other.strength;
        }
    }
}
