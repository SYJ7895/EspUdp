package com.espressif.esptouch.android.v1;

import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

class EspTouchViewModel {
    EditText apSsidTV;
//    TextView apBssidTV;
    EditText apPasswordEdit;
    EditText serverIpEdit;
    EditText deviceCountEdit;
    RadioGroup packageModeGroup;
    TextView messageView;
    Button confirmBtn;

    String ssid;
    byte[] ssidBytes;
    String bssid;

    CharSequence message;

    boolean confirmEnable;
    boolean islooad;

    void setload(boolean load) {
        islooad = load;
    }

    void invalidateAll() {
        if(islooad){

        }else{
            apSsidTV.setText(ssid);
            messageView.setText(message);
        }

//        apBssidTV.setText(bssid);

        confirmBtn.setEnabled(confirmEnable);
    }


}
