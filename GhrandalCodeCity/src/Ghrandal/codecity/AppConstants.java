package Ghrandal.codecity;

/** Shared application constants extracted from the original monolithic CodeCityApp. */
final class AppConstants {
    private AppConstants() {}
    static final int REL_NONE        = 0;
    static final int REL_INHERITANCE = 1 << 0;
    static final int REL_COMPOSITION = 1 << 1;
    static final int REL_INVOCATION  = 1 << 2;
    static final int REL_ACCESS      = 1 << 3;
    static final int REL_ALL         = REL_INHERITANCE | REL_COMPOSITION | REL_INVOCATION | REL_ACCESS;
}
