package tungstwenty.xposed.keyboard42dicts;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {

	private static final String MY_PACKAGE_NAME = XposedMod.class.getPackage().getName();

	public static final String PREFS = "ModSettings";
	public static final String PREF_LANGUAGE = "Injected_Language";

	private static final String PACKAGE_KEYBOARD = "com.google.android.inputmethod.latin";

	private String modulePath;
	private SharedPreferences prefs;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		modulePath = startupParam.modulePath;

		// Load the preferences saved by the mod's application
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, PREFS, Context.MODE_PRIVATE);
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

		if (lpparam.packageName.equals(PACKAGE_KEYBOARD)) {
			// Reload the mod settings if they have changed
			AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

			String configuredLang = prefs.getString(PREF_LANGUAGE, null);
			// Hook only needed if a language is actually selected
			if (configuredLang != null) {
				// Detect the lang and optionally the country (if xx_yy notation is used)
				final String lang;
				final String country;
				Matcher regExp = Pattern.compile("(.+?)_(.+)").matcher(configuredLang);
				if (regExp.matches()) {
					// xx_yy notation, extract the 2 tokens
					lang = regExp.group(1);
					country = regExp.group(2);
				} else {
					// '_' doesn't exist, so only the language matters
					lang = configuredLang;
					country = null;
				}

				try {
					/*
					 * Hook the method that fetches a dictionary file for a particular language.
					 * If the language we want is being fetched, fool the method so it goes looking
					 * for the pt_BR language instead (which has been replaced by our own dict file)
					 */
					findAndHookMethod("com.android.inputmethod.latin.DictionaryFactory", lpparam.classLoader,
					    "getMainDictionaryResourceIdIfAvailableForLocale", Resources.class, Locale.class,
					    new XC_MethodHook() {
						    @Override
						    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							    // Fetch the 2nd argument of the method, which is the Locale for which a dictionary is
							    // requested
							    Locale loc = (Locale) param.args[1];
							    if (lang != null) {
								    if (loc.getLanguage().equals(lang)
								            && ((country == null) || loc.getCountry().equals(country))) {
									    // "Our" language is being requested, make the (modified) pt_BR resource be
									    // loaded instead
									    param.args[1] = new Locale("pt", "BR");
								    }
							    }
							    // We ran this before the original method, now let it resume with the changed parameter
						    }
					    });

				} catch (Throwable t) {
					XposedBridge.log(t);
				}
			}
		}
	}

	@SuppressLint("DefaultLocale")
	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {

		if (resparam.packageName.equals(PACKAGE_KEYBOARD)) {
			// Reload the mod settings if they have changed
			AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

			String configuredLang = prefs.getString(PREF_LANGUAGE, null);
			if (configuredLang != null) {
				// Reference the resources from this mod
				XModuleResources modRes = XModuleResources.createInstance(modulePath, resparam.res);

				int resId = modRes.getIdentifier("main_" + configuredLang.toLowerCase(), "raw", MY_PACKAGE_NAME);

				// Replace the pt_BR resource in the target app to our new dict resource
				// NOTE: In this particular app, the package name for the resources is different from the app package
				resparam.res.setReplacement("com.android.inputmethod.latin", "raw", "main_pt_br", modRes.fwd(resId));
			}
		}
	}

}
