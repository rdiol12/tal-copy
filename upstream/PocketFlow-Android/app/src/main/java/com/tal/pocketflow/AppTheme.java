package com.tal.pocketflow;

import android.content.Context;
import android.content.res.Configuration;

/** Exact light/dark color roles used by the user's ShiftHours reference. */
public final class AppTheme {
    private AppTheme() {}
    public static boolean dark(Context context){
        String value=context.getSharedPreferences("prefs",Context.MODE_PRIVATE).getString("appearance","SYSTEM");
        return value.equals("DARK")||(!value.equals("LIGHT")&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
    }
    public static Palette palette(Context c){return new Palette(dark(c));}
    public static final class Palette {
        final boolean dark;
        final int background,ink,muted,primary,onPrimary,container,onContainer,surface,variant,outline,secondary,secondaryContainer,error;
        Palette(boolean dark){
            this.dark=dark;
            background=dark?0xff101720:0xffF4F7FB;ink=dark?0xffE7EDF4:0xff182333;muted=dark?0xffB9C6D5:0xff4E5D70;
            primary=dark?0xffADC7FF:0xff255DD2;onPrimary=dark?0xff002D73:0xffFFFFFF;container=dark?0xff203C67:0xffE3ECFF;onContainer=dark?0xffDFE9FF:0xff15356C;
            surface=dark?0xff19232E:0xffFFFFFF;variant=dark?0xff263441:0xffEBF0F5;outline=dark?0xff374757:0xffDBE3ED;
            secondary=dark?0xff80D6CB:0xff087F82;secondaryContainer=dark?0xff174C49:0xffDDF3EF;error=dark?0xffFFB1C0:0xffB3263D;
        }
    }
}
