package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheral;
import com.adafruit.bluefruit.le.connect.ble.central.BlePeripheralUart;
import com.adafruit.bluefruit.le.connect.ble.central.BleScanner;
import com.adafruit.bluefruit.le.connect.ble.central.UartDataManager;
import com.adafruit.bluefruit.le.connect.style.UartStyle;
import com.adafruit.bluefruit.le.connect.utils.DialogUtils;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OldAWSIoTFragment extends ConnectedPeripheralFragment implements UartDataManager.UartDataManagerListener {
    // Log
    private final static String TAG = OldAWSIoTFragment.class.getSimpleName();

    // Data
    private UartDataManager mUartDataManager;
    private long mOriginTimestamp;
    private List<BlePeripheralUart> mBlePeripheralsUart = new ArrayList<>();
    private Map<String, DashPathEffect> mLineDashPathEffectForPeripheral = new HashMap<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // region Fragment Lifecycle
    public static OldAWSIoTFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        OldAWSIoTFragment fragment = new OldAWSIoTFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public OldAWSIoTFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_awsiot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.awsiot_tab_title);

        // UI

        // Setup
        Context context = getContext();
        if (context != null) {
            mUartDataManager = new UartDataManager(context, this, true);
            mOriginTimestamp = System.currentTimeMillis();

            setupUart();
        }
    }

    @Override
    public void onDestroy() {
        if (mUartDataManager != null) {
            Context context = getContext();
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        if (mBlePeripheralsUart != null) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart) {
                blePeripheralUart.uartDisable();
            }
            mBlePeripheralsUart.clear();
            mBlePeripheralsUart = null;
        }

        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();

        switch (item.getItemId()) {
            case R.id.action_help:
                if (activity != null) {
                    FragmentManager fragmentManager = activity.getSupportFragmentManager();
                    if (fragmentManager != null) {
                        CommonHelpFragment helpFragment = CommonHelpFragment.newInstance(getString(R.string.awsiot_help_title), getString(R.string.awsiot_help_text));
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction()
                                .replace(R.id.contentLayout, helpFragment, "Help");
                        fragmentTransaction.addToBackStack(null);
                        fragmentTransaction.commit();
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // endregion


    // region Uart

    private boolean isInMultiUartMode() {
        return mBlePeripheral == null;
    }

    private void setupUart() {
        // Line dashes assigned to peripherals
        final DashPathEffect[] dashPathEffects = UartStyle.defaultDashPathEffects();

        // Enable uart
        if (isInMultiUartMode()) {
            mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
            List<BlePeripheral> connectedPeripherals = BleScanner.getInstance().getConnectedPeripherals();
            for (int i = 0; i < connectedPeripherals.size(); i++) {
                BlePeripheral blePeripheral = connectedPeripherals.get(i);
                mLineDashPathEffectForPeripheral.put(blePeripheral.getIdentifier(), dashPathEffects[i % dashPathEffects.length]);

                if (!BlePeripheralUart.isUartInitialized(blePeripheral, mBlePeripheralsUart)) {
                    BlePeripheralUart blePeripheralUart = new BlePeripheralUart(blePeripheral);
                    mBlePeripheralsUart.add(blePeripheralUart);
                    blePeripheralUart.uartEnable(mUartDataManager, status -> {

                        String peripheralName = blePeripheral.getName();
                        if (peripheralName == null) {
                            peripheralName = blePeripheral.getIdentifier();
                        }

                        String finalPeripheralName = peripheralName;
                        mMainHandler.post(() -> {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                // Done
                                Log.d(TAG, "Uart enabled for: " + finalPeripheralName);
                            } else {
                                //WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                AlertDialog dialog = builder.setMessage(String.format(getString(R.string.uart_error_multipleperiperipheralinit_format), finalPeripheralName))
                                        .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        /*
                                            BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }*/
                                        })
                                        .show();
                                DialogUtils.keepDialogOnOrientationChanges(dialog);
                            }
                        });

                    });
                }
            }

        } else {       //  Single peripheral mode
            if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
                mLineDashPathEffectForPeripheral.clear();   // Reset line dashes assigned to peripherals
                mLineDashPathEffectForPeripheral.put(mBlePeripheral.getIdentifier(), dashPathEffects[0]);
                BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
                mBlePeripheralsUart.add(blePeripheralUart);
                blePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Done
                        Log.d(TAG, "Uart enabled");
                    } else {
                        Context context = getContext();
                        if (context != null) {
                            WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                        BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                        if (strongBlePeripheralUart != null) {
                                            strongBlePeripheralUart.disconnect();
                                        }
                                    })
                                    .show();
                            DialogUtils.keepDialogOnOrientationChanges(dialog);
                        }
                    }
                }));
            }
        }
    }

    // endregion

    // region Data Handling/Sending

    private void addEntry(@NonNull String peripheralIdentifier, int index, float value, float timestamp) {
        Log.d(TAG, "=================================== Add Entry: " + value);
    }

    private void notifyDataSetChanged() {
      
        // DW - This might be where we put in a command to send data to AWS IoT.
    }

    // endregion

    // region UartDataManagerListener
    private static final byte kLineSeparator = 10;

    @Override
    public void onUartRx(@NonNull byte[] data, @Nullable String peripheralIdentifier) {
        /*
        Log.d(TAG, "uart rx read (hex): " + BleUtils.bytesToHex2(data));
        try {
            Log.d(TAG, "uart rx read (utf8): " + new String(data, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }*/

        // Find last separator
        boolean found = false;
        int i = data.length - 1;
        while (i >= 0 && !found) {
            if (data[i] == kLineSeparator) {
                found = true;
            } else {
                i--;
            }
        }
        final int lastSeparator = i + 1;

        //
        if (found) {
            final byte[] subData = Arrays.copyOfRange(data, 0, lastSeparator);
            final float currentTimestamp = (System.currentTimeMillis() - mOriginTimestamp) / 1000.f;
            final String dataString = new String(subData, StandardCharsets.UTF_8);
            //Log.d(TAG, "data: " + dataString);

            final String[] lineStrings = dataString.replace("\r", "").split("\n");
            for (String lineString : lineStrings) {
//                    Log.d(TAG, "line: " + lineString);
                final String[] valuesStrings = lineString.split("[,; \t]");
                int j = 0;
                for (String valueString : valuesStrings) {
                    boolean isValid = true;
                    float value = 0;
                    if (valueString != null) {
                        try {
                            value = Float.parseFloat(valueString);
                        } catch (NumberFormatException ignored) {
                            isValid = false;
                        }
                    } else {
                        isValid = false;
                    }

                    if (isValid && peripheralIdentifier != null) {
                        //Log.d(TAG, "value " + j + ": (" + currentTimestamp + ", " + value + ")");
                        //Log.d(TAG, "value " + j + ": " + value);
                        addEntry(peripheralIdentifier, j, value, currentTimestamp);
                        j++;
                    }
                }

                mMainHandler.post(this::notifyDataSetChanged);
            }

        }

        mUartDataManager.removeRxCacheFirst(lastSeparator, peripheralIdentifier);
    }

    // endregion
}
