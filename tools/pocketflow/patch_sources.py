#!/usr/bin/env python3
"""Copies the PocketFlow Java sources that are converted for iOS into a work directory,
applying the few edits that remove Android-only pieces (Google sign-in for Drive).

    patch_sources.py <upstream java dir> <work dir> <Name>...

Every edit must match exactly once, so a new upstream version that moves the code fails loudly."""
import re, sys, pathlib

# (file, regular expression, replacement)
EDITS = [
    # The Google Drive card needs Google Play services; manual file backup stays.
    ("MainActivity", r"\n\s*googleDriveCard\(\);", "\n"),
    ("MainActivity", r"\n\s*int availability=com\.google\.android\.gms\.common\.GoogleApiAvailability[^\n]*\n[^\n]*GoogleApiAvailability\.getInstance\(\)\.getErrorDialog[^\n]*", "\n"),
    ("MainActivity", r"\n\s*if\(result\.resolution!=null\)\{\n[^\n]*\n[^\n]*startIntentSenderForResult[^\n]*\n\s*return;\n\s*\}", "\n"),
    ("MainActivity", r"String token=DriveTask\.token\(com\.google\.android\.gms\.auth\.api\.identity\.Identity\.getAuthorizationClient\(this\)\.getAuthorizationResultFromIntent\(data\)\);startDrive\(action,id,token,generation\);", "startDrive(action,id,null,generation);"),
    # Reminders are scheduled through the platform hook instead of Android's JobScheduler.
    ("MainActivity", r"\(\(android\.app\.job\.JobScheduler\)getSystemService\(JOB_SCHEDULER_SERVICE\)\)\.cancel\(7002\);", "ReminderJob.cancel(this);"),
    # The reader look-alike always decodes UTF-8 and takes no JDK charset.
    ("ExchangeRates", r"new InputStreamReader\(connection\.getInputStream\(\),StandardCharsets\.UTF_8\)", "new InputStreamReader(connection.getInputStream())"),
]

# ---- Additions for the iOS build (not in the Android app) ----
# Movements that arrive through a link: salary sent from ShiftHours, or a Wallet tap passed on by Shortcuts.
LINK_METHODS = """
    private Store.Tx linkedDraft;
    /** iOS: opens the new-movement form filled in from a link. Nothing is saved until the user confirms. */
    void openLinkedMovement(String kind,long cents,String date,String merchant,String note,String card,String marker,boolean done){
        Store.Tx draft=new Store.Tx();
        if(!marker.isEmpty())for(Store.Tx old:store.transactions())if(old.kind.equals(kind)&&old.note.contains(marker)){draft.id=old.id;draft.account=old.account;draft.category=old.category;draft.tag=old.tag;draft.rule=old.rule;break;}
        draft.kind=kind;draft.amount=cents;draft.originalAmount=cents;draft.date=date;draft.merchant=merchant;draft.note=note;draft.count=1;draft.status=done?1:0;
        if(draft.tag==null)draft.tag="";
        if(draft.category==null){draft.category=kind.equals("income")?"משכורת":"";if(!merchant.trim().isEmpty())for(Store.Tx old:store.transactions())if(old.merchant.equalsIgnoreCase(merchant)){draft.category=old.category;break;}}
        if(draft.account==0){
            List<Store.Account> accounts=paymentAccounts();long chosen=0;String wanted=card.trim().toLowerCase(Locale.ROOT);
            for(Store.Account a:accounts){boolean credit=a.type.equals("credit")||a.type.equals("fixed");String name=a.name.trim().toLowerCase(Locale.ROOT);
                if(kind.equals("income")?!credit:(!wanted.isEmpty()&&!name.isEmpty()&&(name.contains(wanted)||wanted.contains(name)))){chosen=a.id;break;}}
            if(chosen==0&&!kind.equals("income")){long last=prefs.getLong("lastAccount",0);for(Store.Account a:accounts)if(a.id==last)chosen=last;}
            if(chosen==0&&!accounts.isEmpty())chosen=accounts.get(0).id;
            draft.account=chosen;
        }
        if(kind.equals("income"))prefs.edit().putBoolean("income",true).apply();
        linkedDraft=draft;tab=1;chargesMode=false;
        if(started&&page!=null&&(!appLock.enabled()||unlocked))render();
    }
    private void openLinkedDraft(){
        if(linkedDraft==null||!started||(appLock.enabled()&&!unlocked)||page==null)return;
        Store.Tx draft=linkedDraft;linkedDraft=null;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{for(Dialog dialog:new ArrayList<>(openDialogs))if(dialog.isShowing())dialog.dismiss();if(drawer!=null)drawer.dismiss();transactionDialog(draft);});
    }
    /** iOS: an expense expressed in hours of work, from the hourly pay ShiftHours last sent. */
    private String workHours(long cents){long wage=getSharedPreferences("shifthours",MODE_PRIVATE).getLong("wage",0);return wage<=0?"":"≈ "+String.format(Locale.US,"%.1f",cents/(double)wage)+" שעות עבודה";}
"""
WALLET_CARD = (
    'LinearLayout wallet=card(body);title(wallet,"רישום אוטומטי מ־Apple Pay");'
    'hint(wallet,"אחרי תשלום ב־Apple Pay אפשר לפתוח את PocketFlow עם תנועה מוכנה לאישור: סכום, בית עסק וכרטיס. שום דבר לא נשמר בלי אישור שלך.");'
    'hint(wallet,"הגדרה חד־פעמית לכל כרטיס באפליקציית קיצורי דרך: אוטומציה חדשה ← Wallet (עסקה) ← בחירת הכרטיס ← הפעלה מיידית ← הפעולה Add movement to PocketFlow ← בשדות Amount, Merchant ו־Card בוחרים קלט הקיצור.");'
    'wallet.addView(button("פתיחת קיצורי דרך",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("shortcuts://create-automation")));}catch(Exception e){toast("לא ניתן לפתוח את קיצורי דרך. יש לפתוח את האפליקציה ידנית.");}}));'
    'if(getSharedPreferences("shifthours",MODE_PRIVATE).getLong("wage",0)>0)hint(wallet,"ShiftHours מחובר: הוצאות מוצגות גם בשעות עבודה.");'
)
EDITS += [
    ("MainActivity", r"\n    private void openWidgetAction\(\)\{", LINK_METHODS + "\n    private void openWidgetAction(){"),
    ("MainActivity", r"\n        openWidgetAction\(\);\n        if\(pendingRestore\)showRestorePreview\(\);", "\n        openWidgetAction();openLinkedDraft();\n        if(pendingRestore)showRestorePreview();"),
    ("MainActivity", r'if\(t\.status==0\)amount\.addView\(text\("צפוי · לאישור"', 'if(t.kind.equals("expense")&&!workHours(t.amount).isEmpty())amount.addView(text(workHours(t.amount),12,MUTED));if(t.status==0)amount.addView(text("צפוי · לאישור"'),
    ("MainActivity", r'hint\(summary,"סה״כ הוצאות שבוצעו"\);TextView total=bold\(money\(expense\),32,palette\.onContainer\);total\.setTag\("movements-total"\);summary\.addView\(total\);', 'hint(summary,"סה״כ הוצאות שבוצעו");TextView total=bold(money(expense),32,palette.onContainer);total.setTag("movements-total");summary.addView(total);if(!workHours(expense).isEmpty())hint(summary,workHours(expense));'),
    ("MainActivity", r'LinearLayout planning=card\(body\);title\(planning,"תזכורות ותכנון"\);', WALLET_CARD + 'LinearLayout planning=card(body);title(planning,"תזכורות ותכנון");'),
]
EDITS += [
    # Reminders are planned ahead as local notifications on iOS (ios/ReminderJob.kt).
    ("MainActivity", r"toast\(\"תזכורות פעילות\. נדרשת הרשאת התראות; התזמון נתון להגבלות Android\.\"\);", 'toast("תזכורות פעילות. יש לאשר התראות; התזכורות מתעדכנות בכל פתיחה של האפליקציה.");'),
]

def main():
    source, work, names = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3:]
    work.mkdir(parents=True, exist_ok=True)
    for name in names:
        text = (source / f"{name}.java").read_text(encoding="utf-8")
        for target, pattern, replacement in EDITS:
            if target != name: continue
            text, count = re.subn(pattern, lambda _: replacement, text)
            if count != 1: sys.exit(f"{name}.java: expected exactly one match for {pattern!r}, found {count}")
        (work / f"{name}.java").write_text(text, encoding="utf-8")

if __name__ == "__main__": main()
