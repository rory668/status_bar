/*
package com.studio.statusbar.panel;

/**
 * Class to encapsulate all possible status bar states regarding Keyguard.
 */
public class StatusBarState {

    /**
     * The status bar is in the "normal" shade mode.
     */
    public static final int SHADE = 0;

    /**
     * Status bar is currently the Keyguard.
     */
    public static final int KEYGUARD = 1;

    /**
     * Status bar is in the special mode, where it is fully interactive but still locked. So
     * dismissing the shade will still show the bouncer.
     */
    public static final int SHADE_LOCKED = 2;

    /**
     * Status bar is locked and shows the full screen user switcher.
     */
    public static final int FULLSCREEN_USER_SWITCHER = 3;


    public static String toShortString(int x) {
        switch (x) {
            case SHADE:
                return "SHD";
            case SHADE_LOCKED:
                return "SHD_LCK";
            case KEYGUARD:
                return "KGRD";
            case FULLSCREEN_USER_SWITCHER:
                return "FS_USRSW";
            default:
                return "bad_value_" + x;
        }
    }
}
