package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search over every launchable app: a field on top, the matches under it, OK opens one. */
final class AppSearch {
    private AppSearch() {}

    static String hint(android.content.Context ctx) {
        return ctx.getString(R.string.search_apps);
    }

    static void show(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        if (found.isEmpty()) found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        final List<String> labels = new ArrayList<>(), packages = new ArrayList<>();
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (p.equals(activity.getPackageName()) || packages.contains(p)) continue;
            packages.add(p);
            labels.add(String.valueOf(r.loadLabel(pm)));
        }
        final List<Integer> shown = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<String>());
        Runnable refill = null;
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * density);
        box.setPadding(pad, pad, pad, 0);
        EditText input = new EditText(activity);
        input.setHint(hint(activity));
        input.setSingleLine(true);
        input.setTextSize(18f);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(300 * density)));

        final Runnable fill = () -> {
            String q = input.getText().toString().trim().toLowerCase(Locale.getDefault());
            adapter.clear();
            shown.clear();
            for (int i = 0; i < labels.size(); i++) {
                if (q.isEmpty() || labels.get(i).toLowerCase(Locale.getDefault()).contains(q)) {
                    shown.add(i);
                    adapter.add(labels.get(i));
                }
            }
            adapter.notifyDataSetChanged();
        };
        fill.run();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fill.run(); }
        });

        AlertDialog dialog = new Fonts.Builder(activity).setView(box).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(packages.get(shown.get(position)));
            if (launch == null) launch = pm.getLaunchIntentForPackage(packages.get(shown.get(position)));
            dialog.dismiss();
            if (launch != null) {
                try {
                    activity.startActivity(launch);
                } catch (ActivityNotFoundException ignored) {
                    // the app went away between the list and the click
                }
            }
        });
        if (Expressive.on) {
            // Dialog: extra-large (28dp) corners on surface-container-high, M3.
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Expressive.SURFACE_CONTAINER_HIGH);
            bg.setCornerRadius(28 * density);
            Window w = dialog.getWindow();
            if (w != null) w.setBackgroundDrawable(bg);
            input.setTextColor(Expressive.ON_SURFACE);
            input.setHintTextColor(Expressive.ON_SURFACE_VARIANT);
        }
        dialog.show();
        Fonts.watch(activity, dialog);
        input.requestFocus();
    }
}
