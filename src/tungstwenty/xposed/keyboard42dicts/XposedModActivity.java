package tungstwenty.xposed.keyboard42dicts;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class XposedModActivity extends Activity {

	private static final String REGEXP_LANG_CODE = ".+\\((.+)\\)";

	// Options for the selection dialog box
	private String[] dialogOptions;
	private int dialogSelection;

	@SuppressLint("WorldReadableFiles")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		// Load the list of available languages and sort them alphabetically
		List<String> langs = new LinkedList<String>(
		    Arrays.asList(getResources().getStringArray(R.array.dict_languages)));
		Collections.sort(langs);

		// Add an entry on the beginning for "no language" (keep the stock dictionaries from the app)
		langs.add(0, "None");

		// Check if a language is already configured and if so, note its position to be preselected on the dialog
		final SharedPreferences prefs = getSharedPreferences(XposedMod.PREFS, Context.MODE_WORLD_READABLE);
		String configuredLang = prefs.getString(XposedMod.PREF_LANGUAGE, null);
		updateSelectedLanguage(configuredLang, false);
		int selectionPos = 0;
		Matcher regExp = Pattern.compile(REGEXP_LANG_CODE).matcher("");
		if (configuredLang != null) {
			// Iterate though all entries starting at the 2nd one and check the position of the selection
			for (int i = 1; i < langs.size(); i++) {
				// Reuse the regExp for the current language
				regExp.reset(langs.get(i));
				if (regExp.matches()) {
					// The entry contains a (..) part, get its contents
					String entryLang = regExp.group(1);
					if (entryLang.equals(configuredLang)) {
						// The contents between (..) are the selected ones, this is the position we want
						selectionPos = i;
						// Update the displayed language
						updateSelectedLanguage(langs.get(i), false);
						break;
					}
				}
			}
		} else {
			// No language is configured
			updateSelectedLanguage(null, false);
		}

		// Store the available options and selection in variables for access by the anonymous class
		dialogOptions = langs.toArray(new String[langs.size()]);
		dialogSelection = selectionPos;

		// Trigger a dialog box whenever clicking on the button to select language
		((Button) findViewById(R.id.btnSetLanguage)).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				/*
				 * Create a dialog box with:
				 * - parent activity (the current window)
				 * - one of the standard dialog icons
				 * - a title
				 * - the entries to be displayed, with a certain pre-selection
				 * - an OK button, which will trigger saving the (new) selection
				 * - a Cancel button to dismiss the dialog
				 * The dialog is then displayed.
				 */
				new AlertDialog.Builder(XposedModActivity.this).setIconAttribute(android.R.attr.alertDialogIcon)
				    .setTitle("Select language")
				    .setSingleChoiceItems(dialogOptions, dialogSelection, new DialogInterface.OnClickListener() {

					    @Override
					    public void onClick(DialogInterface dialog, int which) {
						    // Ignore, we'll find the selection if OK is pressed
					    }
				    }).setPositiveButton("Ok", new DialogInterface.OnClickListener() {

					    @Override
					    public void onClick(DialogInterface dialog, int whichButton) {

						    // Find which of the listed items is selected
						    ListView lv = ((AlertDialog) dialog).getListView();
						    // Update the selection position
						    dialogSelection = lv.getCheckedItemPosition();

						    String selectedLang = dialogOptions[dialogSelection];
						    Matcher regExp = Pattern.compile(REGEXP_LANG_CODE).matcher(selectedLang);

						    // Update the saved preferences with the selected option
						    // Also update the displayed value on the main screen

						    Editor prefsEditor = prefs.edit();
						    if (dialogSelection > 0 && regExp.matches()) {
							    prefsEditor.putString(XposedMod.PREF_LANGUAGE, regExp.group(1));
							    updateSelectedLanguage(selectedLang, true);
						    } else {
							    prefsEditor.remove(XposedMod.PREF_LANGUAGE);
							    updateSelectedLanguage(null, true);
						    }
						    prefsEditor.commit();
					    }
				    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

					    @Override
					    public void onClick(DialogInterface dialog, int whichButton) {
						    // Ignore; the dialog will be dismissed without changing anything
					    }
				    }).show();
			}
		});
	}

	/**
	 * Updates the displayed language on the main window.
	 * 
	 * @param language
	 * The text to be displayed in the form "Language (ll)", or null if none has been selected.
	 * 
	 * @param showWarning
	 * If true, a Toast is displayed in case of changing the displayed value warning the
	 * user that configuration changes will only take effect the next time the app is loaded.
	 */
	private void updateSelectedLanguage(String language, boolean showWarning) {

		if (language == null) {
			language = "<None>";
		}

		TextView txtLanguage = (TextView) findViewById(R.id.txtLanguage);
		if (!language.equals(txtLanguage.getText())) {
			// Warn about configuration changes requiring the target app to be reloaded
			Toast.makeText(this,
			    "Configuration changes will only take effect the next time that the keyboard is reloaded.",
			    Toast.LENGTH_SHORT).show();
			txtLanguage.setText(language);
		}
	}

}
