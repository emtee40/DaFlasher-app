package com.atcnetz.patc.daatc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.atcnetz.patc.util.BleUtil;
import com.atcnetz.patc.util.ScannedDevice;

import java.util.ArrayList;
import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

abstract class TextChangedListener<T> implements TextWatcher {
    private T target;

    public TextChangedListener(T target) {
        this.target = target;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        this.onTextChanged(target, s);
    }

    public abstract void onTextChanged(T target, Editable s);
}

@RuntimePermissions
public class ScanActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    private BluetoothAdapter mBTAdapter;
    private DeviceAdapter mDeviceAdapter;
    private boolean mIsScanning;
    private Button button;
    private EditText scanFilterEdit;
    private Switch rssiSortSwitch;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDeviceAdapter.update(result.getDevice(), result.getRssi());
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_scan);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("https://atcnetz.de/privacy_policy.html"));
                startActivity(intent);
            }
        });
        scanFilterEdit = (EditText) findViewById(R.id.scanFilterInput);
        scanFilterEdit.addTextChangedListener(new TextChangedListener<EditText>(scanFilterEdit) {
            @Override
            public void onTextChanged(EditText target, Editable s) {
                mDeviceAdapter.setNameFilter(scanFilterEdit.getText().toString());
            }
        });
        rssiSortSwitch = (Switch) findViewById(R.id.switch1);
        rssiSortSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDeviceAdapter.setSortByRSSI(isChecked);
            }
        });

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBTAdapter.isEnabled()) stopScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDeviceAdapter.clear();
        if (mBTAdapter.isEnabled()) startScan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mIsScanning) {
            menu.findItem(R.id.action_scan).setVisible(false);
            menu.findItem(R.id.action_stop).setVisible(true);
        } else {
            menu.findItem(R.id.action_scan).setVisible(true);
            menu.findItem(R.id.action_stop).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // ignore
            return true;
        } else if (itemId == R.id.action_scan) {
            ScanActivityPermissionsDispatcher.startScanWithPermissionCheck(this);
            return true;
        } else if (itemId == R.id.action_stop) {
            stopScan();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLeScan(final BluetoothDevice newDeivce, final int newRssi,
                         final byte[] newScanRecord) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDeviceAdapter.update(newDeivce, newRssi);
            }
        });
    }

    private void init() {
        // BLE check
        if (!BleUtil.isBLESupported(this)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // BT check
        BluetoothManager manager = BleUtil.getManager(this);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            Toast.makeText(this, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!mBTAdapter.isEnabled()) {
            Toast.makeText(this, R.string.bt_disabled, Toast.LENGTH_SHORT).show();
            finish();
        } else {

            // init listview
            ListView deviceListView = (ListView) findViewById(R.id.list);
            mDeviceAdapter = new DeviceAdapter(this, R.layout.listitem_device,
                    new ArrayList<ScannedDevice>());
            deviceListView.setAdapter(mDeviceAdapter);
            deviceListView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterview, View view, int position, long id) {
                    ScannedDevice item = mDeviceAdapter.getItem(position);
                    if (item != null) {
                        // stop before change Activity
                        stopScan();
                        Intent intent = new Intent(view.getContext(), DeviceActivity.class);
                        BluetoothDevice selectedDevice = item.getDevice();
                        intent.putExtra(DeviceActivity.EXTRA_BLUETOOTH_DEVICE, selectedDevice);
                        startActivity(intent);

                    }
                }
            });

            stopScan();

            startScan();
        }
    }

    boolean popup_was_shown = false;
    boolean popup_was_shown1 = false;

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void startScan() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if ((this.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)||(this.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
                    if (!popup_was_shown1) {
                        popup_was_shown1 = true;
                        if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)  != PackageManager.PERMISSION_GRANTED){
                            AlertDialog.Builder alertDialogBuilder1 = new AlertDialog.Builder(this);
                            alertDialogBuilder1.setMessage("This app does need \"bluetooth connect permissions\" to connect to BLE devices.");
                            alertDialogBuilder1.setTitle("BLE Connect permission");
                            alertDialogBuilder1.setNegativeButton("ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                                }
                            });
                            AlertDialog alertDialog1 = alertDialogBuilder1.create();
                            alertDialog1.show();
                        }
                    }
                    if (!popup_was_shown) {
                        popup_was_shown = true;
                        if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED){
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                        alertDialogBuilder.setMessage("This app needs location data to enable bluetooth scanning even when the app is closed or not in use.");
                        alertDialogBuilder.setTitle("Location permission");
                        alertDialogBuilder.setNegativeButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface1, int i) {
                                dialogInterface1.cancel();
                                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
                            }
                        });
                        AlertDialog alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                        }
                    }
                } else {
                    mBTAdapter.getBluetoothLeScanner().startScan(mLeScanCallback);
                    mIsScanning = true;
                    setProgressBarIndeterminateVisibility(true);
                    invalidateOptionsMenu();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (!popup_was_shown) {
                        popup_was_shown = true;
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                        alertDialogBuilder.setMessage("This app collects location data to enable bluetooth scanning even when the app is closed or not in use.");
                        alertDialogBuilder.setTitle("Location permission");
                        alertDialogBuilder.setNegativeButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        });
                        AlertDialog alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                    }
                } else {
                    mBTAdapter.getBluetoothLeScanner().startScan(mLeScanCallback);
                    mIsScanning = true;
                    setProgressBarIndeterminateVisibility(true);
                    invalidateOptionsMenu();
                }
            } else {
                mBTAdapter.startLeScan(this);
                mIsScanning = true;
                setProgressBarIndeterminateVisibility(true);
                invalidateOptionsMenu();
            }
        }
    }

    private void stopScan() {
        if (mBTAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                mBTAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
            } else {
                mBTAdapter.stopLeScan(this);
            }
        }
        mIsScanning = false;
        setProgressBarIndeterminateVisibility(false);
        invalidateOptionsMenu();
    }
}
