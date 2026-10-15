package fr.damien.m832print;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.print.PrintJobInfo;
import android.printservice.PrintService;

/** Native density dialog launched from Android's print preview. */
public final class AdvancedOptionsActivity extends Activity {
    static final String DENSITY_OPTION = "fr.damien.m832print.density";
    private AlertDialog dialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PrintJobInfo incoming = getIntent().getParcelableExtra(PrintService.EXTRA_PRINT_JOB_INFO);
        if (incoming == null) { finish(); return; }

        int saved = incoming.hasAdvancedOption(DENSITY_OPTION)
                ? incoming.getAdvancedIntOption(DENSITY_OPTION) : 2;
        final int[] selected = {saved >= 1 && saved <= 3 ? saved : 2};
        dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(R.string.density_title)
                .setSingleChoiceItems(R.array.density_choices, selected[0] - 1, (shown, which) -> selected[0] = which + 1)
                .setNegativeButton(R.string.cancel, (shown, which) -> finish())
                .setPositiveButton(R.string.apply, (shown, which) -> {
                    PrintJobInfo.Builder builder = new PrintJobInfo.Builder(incoming);
                    builder.putAdvancedOption(DENSITY_OPTION, selected[0]);
                    Intent result = new Intent();
                    result.putExtra(PrintService.EXTRA_PRINT_JOB_INFO, builder.build());
                    setResult(RESULT_OK, result);
                    finish();
                })
                .setOnCancelListener(shown -> finish())
                .create();
        dialog.show();
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
