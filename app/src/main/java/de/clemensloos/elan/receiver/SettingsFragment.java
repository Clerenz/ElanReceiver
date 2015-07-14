package de.clemensloos.elan.receiver;



import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.widget.Toast;



public class SettingsFragment extends PreferenceFragment {

	
	Preference portPreference;
	Preference textsizePreference;
	SharedPreferences sharedPrefs;
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_general);
        
        // Load the shared preferences
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        String port = sharedPrefs.getString(
        		getResources().getString(R.string.pref_port_key),
        		getResources().getString(R.string.pref_port_default));
        String textsize = sharedPrefs.getString(
        		getResources().getString(R.string.pref_textsize_key),
        		getResources().getString(R.string.pref_textsize_default));
        
        
        // Preference for listening port
        portPreference = findPreference(getResources().getString(R.string.pref_port_key));
        portPreference.setSummary(getString(R.string.pref_port_desc) + " " + port);
        // Add the on change listener to refresh the preferences summary
        portPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				// Verify the value is an integer
			    if( !newValue.toString().equals("")  &&  newValue.toString().matches("\\d*") ) {
			        int port = Integer.parseInt(newValue.toString());
			        // Verify value is within acceptable range of ports
			        if( port < 49152 || port > 65535) {
			        	Toast.makeText(SettingsFragment.this.getActivity(), R.string.toast_port_hint, Toast.LENGTH_LONG).show();
			        	return false;
			        }
			        portPreference.setSummary(
			        		getString(R.string.pref_port_desc) + " " + newValue.toString());
			    	return true;
			    }
			    else {
			        Toast.makeText(SettingsFragment.this.getActivity(), R.string.toast_port_invalid, Toast.LENGTH_LONG).show();
			        return false;
			    }
			}
		});
        
        
        // Preference for text size
        textsizePreference = findPreference(getResources().getString(R.string.pref_textsize_key));
        textsizePreference.setSummary(getString(R.string.pref_textsize_desc) + " " + textsize + " dp");
     // Add the on change listener to refresh the preferences summary
        textsizePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				textsizePreference.setSummary(
						getString(R.string.pref_textsize_desc) + " " + newValue.toString() + " dp");
				return true;
			}
		});
    }
	
	
	
}
