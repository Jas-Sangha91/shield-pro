package com.jas.shieldhome;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.jas.shieldhome.Layout.Folder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.jas.shieldhome.Ui.DOT;

/** All the hold-OK menus for apps and folders, on Home and on the Apps page. */
final class Menus {

    private final MainActivity a;

    Menus(MainActivity a) {
        this.a = a;
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private String label(String pkg) {
        return a.installed.get(pkg).loadLabel(a.pm).toString();
    }

    private void toast(String text) {
        Toast.makeText(a, text, Toast.LENGTH_SHORT).show();
    }

    // ---------- Apps on Home ----------

    void appMenu(final String pkg) {
        final Layout l = a.layout;
        final boolean isDock = l.dock.contains(pkg);
        final Folder inFolder = l.folderOf(pkg);
        final List<String> opts = new ArrayList<>();
        opts.add("Open");
        if (inFolder == null) opts.add("Move");
        if (!isDock && inFolder == null) opts.add("Move to group");
        if (!isDock) opts.add(inFolder == null ? "Add to folder" : "Move to another folder");
        if (inFolder != null) opts.add("Take out of folder");
        opts.add(isDock ? "Remove from dock" : "Add to dock");
        opts.add("Remove from Home");
        opts.add("Cancel");

        dialog().setTitle(label(pkg))
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    switch (opts.get(which)) {
                        case "Open":
                            a.launch(pkg);
                            break;
                        case "Move":
                            a.startMove(pkg);
                            break;
                        case "Move to group":
                            chooseGroup(pkg);
                            break;
                        case "Add to folder":
                        case "Move to another folder":
                            chooseFolder(pkg);
                            break;
                        case "Take out of folder":
                            l.takeOutOfFolder(pkg);
                            a.saveAndRender(pkg);
                            break;
                        case "Add to dock":
                            l.addToDock(pkg);
                            a.saveAndRender(pkg);
                            break;
                        case "Remove from dock":
                            l.removeFromDock(pkg);
                            a.saveAndRender(pkg);
                            break;
                        case "Remove from Home":
                            removeFromHome(pkg);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    // ---------- Apps page ----------

    void appsPageMenu(final String pkg) {
        final Layout l = a.layout;
        final boolean onHome = l.isOnHome(pkg);
        final boolean isDock = l.dock.contains(pkg);
        final List<String> opts = new ArrayList<>();
        opts.add("Open");
        if (!onHome) opts.add("Add to Home");
        opts.add(isDock ? "Remove from dock" : "Add to dock");
        if (onHome) opts.add("Remove from Home");
        opts.add("Cancel");

        dialog().setTitle(label(pkg))
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    switch (opts.get(which)) {
                        case "Open":
                            a.launch(pkg);
                            break;
                        case "Add to Home":
                            chooseHomeSpot(pkg);
                            break;
                        case "Add to dock":
                            l.addToDock(pkg);
                            a.saveAndRender(pkg);
                            break;
                        case "Remove from dock":
                            l.removeFromDock(pkg);
                            a.saveAndRender(pkg);
                            break;
                        case "Remove from Home":
                            removeFromHome(pkg);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void chooseHomeSpot(final String pkg) {
        final Layout l = a.layout;
        final List<String> names = new ArrayList<>();
        final List<Object> targets = new ArrayList<>();
        for (String g : Layout.GROUP_ORDER) {
            names.add(Layout.groupName(g));
            targets.add(g);
        }
        for (Folder f : l.folders.values()) {
            names.add(f.name + " folder   " + DOT + "   " + Layout.groupName(f.group));
            targets.add(f);
        }
        dialog().setTitle("Add to Home")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    Object t = targets.get(which);
                    if (t instanceof Folder) {
                        Folder f = (Folder) t;
                        l.addToHomeFolder(pkg, f);
                        toast("Added to Home in " + f.name);
                    } else {
                        String g = (String) t;
                        l.addToHome(pkg, g);
                        toast("Added to Home in " + Layout.groupName(g));
                    }
                    a.saveAndRender(pkg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFromHome(String pkg) {
        a.layout.removeFromHome(pkg);
        toast("Removed from Home. It's still in Apps.");
        a.saveAndRender(pkg);
    }

    // ---------- Folders ----------

    void folderMenu(final Folder f) {
        final Layout l = a.layout;
        final String key = Layout.keyOf(f);
        final List<String> opts = new ArrayList<>();
        opts.add("Open");
        opts.add("Move");
        opts.add("Rename");
        opts.add("Move to group");
        opts.add("Remove folder (keeps the apps)");
        opts.add("Cancel");

        dialog().setTitle(f.name)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String o = opts.get(which);
                    if (o.equals("Open")) {
                        a.openFolder(f);
                    } else if (o.equals("Move")) {
                        a.startMove(key);
                    } else if (o.equals("Rename")) {
                        askFolderName("Rename folder", f.name, name -> {
                            f.name = name;
                            a.saveAndRender(key);
                        });
                    } else if (o.equals("Move to group")) {
                        chooseGroup(key);
                    } else if (o.startsWith("Remove folder")) {
                        String first = l.removeFolder(f);
                        a.saveAndRender(first);
                    }
                })
                .show();
    }

    private void chooseFolder(final String pkg) {
        final Layout l = a.layout;
        final Folder current = l.folderOf(pkg);
        final List<Folder> options = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        names.add("+  New folder");
        for (Folder f : l.folders.values()) {
            if (f == current) continue;
            options.add(f);
            names.add(f.name + "   " + DOT + "   " + Layout.groupName(f.group));
        }
        dialog().setTitle(current == null ? "Add to folder" : "Move to another folder")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        askFolderName("New folder", "", name -> a.saveAndRender(l.createFolderWith(pkg, name)));
                    } else {
                        Folder f = options.get(which - 1);
                        l.putInFolder(pkg, f);
                        toast("Added to " + f.name);
                        a.saveAndRender(Layout.keyOf(f));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askFolderName(String title, String current, final Consumer<String> done) {
        final EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setHint("Folder name");
        input.setText(current);
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(a);
        box.setPadding(a.dp(24), a.dp(8), a.dp(24), 0);
        box.addView(input);
        dialog().setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String name = input.getText().toString().trim();
                    done.accept(name.isEmpty() ? "Folder" : name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Sections ----------

    private void chooseGroup(final String key) {
        final Layout l = a.layout;
        String current = l.groupOf(key);
        final List<String> options = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (String g : Layout.GROUP_ORDER) {
            if (!g.equals(current)) {
                options.add(g);
                names.add(Layout.groupName(g));
            }
        }
        dialog().setTitle("Move to group")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    l.setGroup(key, options.get(which));
                    a.saveAndRender(key);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
