package com.p4f.objecttracking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * show list of BLE devices
 */
public class DevicesFragment extends ListFragment {

    private enum ScanState { NONE, LESCAN, DISCOVERY, DISCOVERY_FINISHED }
    private ScanState                       scanState = ScanState.NONE;
    private static final long               LESCAN_PERIOD = 10000; // similar to bluetoothAdapter.startDiscovery
    private Handler                         leScanStopHandler = new Handler();
    private BluetoothAdapter.LeScanCallback leScanCallback;
    private BroadcastReceiver               discoveryBroadcastReceiver;
    private IntentFilter                    discoveryIntentFilter;

    private Menu                            menu;
    private BluetoothAdapter                bluetoothAdapter;

    public static BluetoothSocket socket = null;
    public static OutputStream outputStream = null;
    public static boolean isConnected = false;


    private ArrayList<BluetoothDevice>      listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice>   listAdapter;
    private int mSelectedDevicePos = -1;
    private int mBkpTxtColor = 0xB3FFFFFF;
    private String mSelectedDeviceAddress = "";
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth

    public DevicesFragment() {
        leScanCallback = (device, rssi, scanRecord) -> {
            if(device != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> { updateScan(device); });
            }
        };
        discoveryBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device != null&& getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateScan(device));
                    }
                }
                if(intent.getAction().equals((BluetoothAdapter.ACTION_DISCOVERY_FINISHED))) {
                    scanState = ScanState.DISCOVERY_FINISHED; // don't cancel again
                    stopScan();
                }
            }
        };
        discoveryIntentFilter = new IntentFilter();
        discoveryIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        discoveryIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    }

    private void enableBluetooth() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        // Gán listAdapter (không thay đổi)
        listAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), 0, listItems) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                BluetoothDevice device = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);

                if (device.getName() == null || device.getName().isEmpty())
                    text1.setText("<unnamed>");
                else
                    text1.setText(device.getName());

                text1.setTextColor(mBkpTxtColor);

                if (mSelectedDeviceAddress.equals(device.getAddress())) {
                    String txt = text1.getText().toString() + " ==> Selected";
                    text1.setText(txt);
                    text1.setTextColor(Color.GREEN);
                    mSelectedDevicePos = position + 1;
                }

                text2.setText(device.getAddress());
                return view;
            }
        };

        // 👉 Chỉ gọi enable Bluetooth nếu có quyền
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        1001);
            } else {
                enableBluetooth();
            }
        } else {
            // Android dưới 12 thì không cần xin permission này
            enableBluetooth();
        }

        mSelectedDeviceAddress = getArguments().getString("device");
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.layout.menu_devices, menu);
        this.menu = menu;
        if (bluetoothAdapter == null) {
            menu.findItem(R.id.bt_settings).setEnabled(false);
            menu.findItem(R.id.ble_scan).setEnabled(false);
        } else if(!bluetoothAdapter.isEnabled()) {
            menu.findItem(R.id.ble_scan).setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(discoveryBroadcastReceiver, discoveryIntentFilter);
        if(bluetoothAdapter == null) {
            setEmptyText("<bluetooth LE not supported>");
        } else if(!bluetoothAdapter.isEnabled()) {
            setEmptyText("<bluetooth is disabled>");
            if (menu != null) {
                listItems.clear();
                listAdapter.notifyDataSetChanged();
                menu.findItem(R.id.ble_scan).setEnabled(false);
            }
        } else {
            setEmptyText("<use SCAN to refresh devices>");
            if (menu != null){
                menu.findItem(R.id.ble_scan).setEnabled(true);
                menu.performIdentifierAction(R.id.ble_scan, 0);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScan();
        getActivity().unregisterReceiver(discoveryBroadcastReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        menu = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.ble_scan) {
            startScan();
            return true;
        } else if (id == R.id.ble_scan_stop) {
            stopScan();
            return true;
        } else if (id == R.id.bt_settings) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (scanState != ScanState.NONE)
            return;

        // 👉 Bắt buộc dùng DISCOVERY vì ESP32 dùng Bluetooth Classic (SPP)
        scanState = ScanState.DISCOVERY;

        // Kiểm tra quyền location (bắt buộc từ Android 6.0 trở lên)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                scanState = ScanState.NONE;
                new AlertDialog.Builder(getActivity())
                        .setTitle("Location Permission Needed")
                        .setMessage("Location permission is needed to scan for Bluetooth devices.")
                        .setPositiveButton(android.R.string.ok,
                                (dialog, which) -> requestPermissions(
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0))
                        .show();
                return;
            }

            // Kiểm tra GPS đã bật chưa
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            boolean locationEnabled = false;
            try {
                locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {}

            if (!locationEnabled) {
                new AlertDialog.Builder(getActivity())
                        .setTitle("Location Required")
                        .setMessage("Please enable Location Services (GPS or Network) to scan for Bluetooth devices.")
                        .setPositiveButton("OK", null)
                        .show();
                scanState = ScanState.NONE;
                return;
            }
        }

        // 👉 Kiểm tra quyền BLUETOOTH_SCAN với Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1010);
                scanState = ScanState.NONE;
                return;
            }
        }

        // ✅ Bắt đầu quét
        listItems.clear();
        listAdapter.notifyDataSetChanged();
        setEmptyText("<scanning...>");
        menu.findItem(R.id.ble_scan).setVisible(false);
        menu.findItem(R.id.ble_scan_stop).setVisible(true);

        boolean started = bluetoothAdapter.startDiscovery();
        if (!started) {
            scanState = ScanState.NONE;
            setEmptyText("<scan failed to start>");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Nếu bị từ chối, hiện dialog cảnh báo
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getText(R.string.location_denied_title));
            builder.setMessage(getText(R.string.location_denied_message));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return;
        }

        switch (requestCode) {
            case 1001:  // BLUETOOTH_CONNECT
                enableBluetooth();
                break;

            case 0:  // ACCESS_COARSE_LOCATION
                new Handler(Looper.getMainLooper()).postDelayed(this::startScan, 1);
                break;

            case 1010:  // BLUETOOTH_SCAN
                startScan();  // Gọi lại scan sau khi được cấp quyền
                break;
        }
    }



    private void updateScan(BluetoothDevice device) {
        if(scanState == ScanState.NONE)
            return;
        if(listItems.indexOf(device) < 0) {
            listItems.add(device);
            Collections.sort(listItems, DevicesFragment::compareTo);
            listAdapter.notifyDataSetChanged();
        }
    }

    private void stopScan() {
        if(scanState == ScanState.NONE)
            return;
        setEmptyText("<no bluetooth devices found>");
        if(menu != null) {
            menu.findItem(R.id.ble_scan).setVisible(true);
            menu.findItem(R.id.ble_scan_stop).setVisible(false);
        }
        switch(scanState) {
            case LESCAN:
                leScanStopHandler.removeCallbacks(this::stopScan);
                bluetoothAdapter.stopLeScan(leScanCallback);
                break;
            case DISCOVERY:
                bluetoothAdapter.cancelDiscovery();
                break;
            default:
                // already canceled
        }
        scanState = ScanState.NONE;

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        stopScan();
        BluetoothDevice device = listItems.get(position-1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1001);
            return;
        }

        TextView tv = (TextView) v.findViewById(R.id.text1);
        String txt = tv.getText().toString();
        txt = device.getName() + "==> Selected ";
        tv.setText(txt);

        if(position != mSelectedDevicePos && mSelectedDevicePos != -1){
            View listView = l.getChildAt(mSelectedDevicePos);
            TextView tvpre = (TextView) listView.findViewById(R.id.text1);
            String txtpre = tvpre.getText().toString();
            txtpre = listItems.get(mSelectedDevicePos-1).getName();
            tvpre.setText(txtpre);
            tvpre.setTextColor(mBkpTxtColor);
        }

        mSelectedDevicePos = position;

        tv.setTextColor(Color.GREEN);

        getTargetFragment().onActivityResult(
                getTargetRequestCode(),
                Activity.RESULT_OK,
                new Intent().putExtra("bluetooth device", device.getAddress())
        );

        mSelectedDeviceAddress = device.getAddress();
        connectToDevice(device.getAddress());

//        Bundle args = new Bundle();
//        args.putString("device", device.getAddress());
//        Fragment fragment = new TerminalFragment();
//        fragment.setArguments(args);
//        getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
    }

    /**
     * sort by name, then address. sort named devices first
     */
    static int compareTo(BluetoothDevice a, BluetoothDevice b) {


        boolean aValid = a.getName()!=null && !a.getName().isEmpty();
        boolean bValid = b.getName()!=null && !b.getName().isEmpty();
        if(aValid && bValid) {
            int ret = a.getName().compareTo(b.getName());
            if (ret != 0) return ret;
            return a.getAddress().compareTo(b.getAddress());
        }
        if(aValid) return -1;
        if(bValid) return +1;
        return a.getAddress().compareTo(b.getAddress());
    }

    private void connectToDevice(String address) {
        new Thread(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                UUID sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                socket = device.createRfcommSocketToServiceRecord(sppUUID);
                socket.connect(); // Blocking

                outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream(); // nếu cần sau này

                isConnected = true;

                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Kết nối thành công", Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (IOException e) {
                Log.e("BT_CONNECT", "Lỗi kết nối: " + e.getMessage());
                isConnected = false;

                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Không thể kết nối Bluetooth", Toast.LENGTH_SHORT).show()
                    );
                }

                try {
                    if (socket != null) socket.close();
                } catch (IOException closeEx) {
                    Log.e("BT_CONNECT", "Lỗi đóng socket: " + closeEx.getMessage());
                }
            }
        }).start();
    }



}
