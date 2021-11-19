package com.db.bluetoothsample;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.login.LoginException;

public class MainActivity extends AppCompatActivity {
    // will show the statuses like bluetooth open, close or data sent
    TextView myLabel;

    // will enable user to enter any text to be printed
    EditText myTextbox;

    // android built in classes for bluetooth operations
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    // needed for communication to bluetooth device / network
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    //Mine
    ListView myGroup;
    RadioGroup myGroup2;
    private RadioButton radioButton;
    //ArrayList<String> listItems=new ArrayList<String>();
    ArrayAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // we are going to have three buttons for specific functions
            Button openButton = (Button) findViewById(R.id.open);
            Button connButton = (Button) findViewById(R.id.connect);
            Button sendButton = (Button) findViewById(R.id.send);
            Button closeButton = (Button) findViewById(R.id.close);

// text label and input box
            myLabel = (TextView) findViewById(R.id.label);
            myTextbox = (EditText) findViewById(R.id.entry);

            // find BT Devices
            openButton.setOnClickListener(v -> {
                findBT();
            });

            // Make connection to BT Device
            connButton.setOnClickListener(view -> {
                try {
                    openBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // send data typed by the user to be printed
            sendButton.setOnClickListener(v -> {
                try {
                    sendData();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            // close bluetooth connection
            closeButton.setOnClickListener(v -> {
                try {
                    closeBT();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // this will find a bluetooth printer device
    void findBT() {

        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if(mBluetoothAdapter == null) {
                myLabel.setText(R.string.btM1);
            }

            if(!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
                findBT();
            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            //Mine
            ArrayList list = new ArrayList();
            myGroup = findViewById(R.id.listV);
            myGroup2 = findViewById(R.id.radioG);
            if(pairedDevices.size() > 0) {
                //ListView
                /*for (BluetoothDevice device : pairedDevices) {
                    String devicename = device.getName();
                    String macAddress = device.getAddress();
                    list.add("Name: "+devicename+"\nMAC Address: "+macAddress);
                }
                adapter = new ArrayAdapter<>(getApplicationContext(),
                        android.R.layout.simple_list_item_1, list);
                myGroup.setAdapter(adapter);*/

                /*for (BluetoothDevice device : pairedDevices) {

                    // RPP300 is the name of the bluetooth printer device
                    // we got this name from the list of paired devices
                    if (device.getName().equals("CS10C Printer")) {
                        mmDevice = device;
                        break;
                    }
                }*/

                //RadioGroup
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    RadioButton rb = new RadioButton(this);
                    rb.setText(deviceName);
                    myGroup2.addView(rb);
                }

                myGroup2.setOnCheckedChangeListener((group, checkedId) -> {
                    // checkedId is the RadioButton selected
                    radioButton = findViewById(checkedId);
                    Toast.makeText(getApplicationContext(), "My Device: "
                            +radioButton.getText(), Toast.LENGTH_LONG).show();
                    for (BluetoothDevice device: pairedDevices) {
                        if (device.getName().contentEquals(radioButton.getText())) {
                            mmDevice = device;
                        }
                    }
                });
            }

            //myLabel.setText(R.string.btM2);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // tries to open a connection to the bluetooth printer device
    void openBT() throws IOException {
        try {

            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            Log.e("Check Connection", "Connection Status: " + mmSocket.isConnected());
            if (!mmSocket.isConnected()) {
                Toast.makeText(getApplicationContext(), "Connection Error, Press open again", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Connected Device is " + mmDevice.getName(), Toast.LENGTH_LONG).show();
                mmOutputStream = mmSocket.getOutputStream();
                Log.e("mmOutputStream", String.valueOf(mmOutputStream));
                mmInputStream = mmSocket.getInputStream();

                beginListenForData();

                myLabel.setText(R.string.btM3);
                myGroup2.removeAllViews();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * after opening a connection to bluetooth printer device,
     * we have to listen and check if a data were sent to be printed.
     */
    void beginListenForData() {
        try {
            final Handler handler = new Handler();

            // this is the ASCII code for a newline character
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            workerThread = new Thread(() -> {

                while (!Thread.currentThread().isInterrupted() && !stopWorker) {

                    try {

                        int bytesAvailable = mmInputStream.available();

                        if (bytesAvailable > 0) {

                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);

                            for (int i = 0; i < bytesAvailable; i++) {

                                byte b = packetBytes[i];
                                if (b == delimiter) {

                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(
                                            readBuffer, 0,
                                            encodedBytes, 0,
                                            encodedBytes.length
                                    );

                                    // specify US-ASCII encoding
                                    final String data = new String(encodedBytes, StandardCharsets.US_ASCII);
                                    readBufferPosition = 0;

                                    // tell the user data were sent to bluetooth printer device
                                    handler.post(() -> myLabel.setText(data));

                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }

                    } catch (IOException ex) {
                        stopWorker = true;
                    }

                }
            });

            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // this will send text data to be printed by the bluetooth printer
    void sendData() throws IOException {
        try {

            // the text typed by the user
            String msg = myTextbox.getText().toString() + "\n" + "\n" + "\n" + "\n";
            //msg += "\n";
            //Toast.makeText(getApplicationContext(), "My data" + msg, Toast.LENGTH_LONG).show();
            Log.e("My text", "sendData: " + msg);
            Log.e("My outStream", "out: " + Arrays.toString(msg.getBytes()));
            mmOutputStream.write(msg.getBytes());
            Thread.sleep(100);    // added this line

            // tell the user data were sent
            myLabel.setText(R.string.datasnt);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // close the connection to bluetooth printer.
    void closeBT() throws IOException {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            myLabel.setText(R.string.btM4);
            Toast.makeText(getApplicationContext(), "Connection Closed", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}