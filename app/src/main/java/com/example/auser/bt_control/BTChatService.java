package com.example.auser.bt_control;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by auser on 2017/11/17.
 */



public class BTChatService {
    private final BluetoothAdapter btAdapert;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_WaitingConnecting = 1;
    private static final int STATE_CONECTING = 2;
    public static final int STATE_CONNECTED = 3;
    private static final int STATE_STOP = 4;
    private static final UUID UUID_String = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    //private static final UUID UUID_String= UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //BTSPP
    private final Handler btHandler;
    private int btState;
    private static final String TAG = "BT_Chat";
    private AcceptThread btAcceptThread;
    private ConnectingThread btConnectingThread;
    private ConnectedThread btConnectedThread;


    public BTChatService(Context context, Handler handler) {
        btAdapert = BluetoothAdapter.getDefaultAdapter();
        btState = STATE_NORMAL;
        btHandler = handler;
    }

    public int getState() {
        return btState;
    }

    public void serverStart() {
        Log.d(TAG, "ENTER SERVER MODE.");
        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptThread != null) {
            btAcceptThread = new AcceptThread();
            btAcceptThread.start();
        }
    }

    public void connect(BluetoothDevice device) {
        Log.d(TAG, "conner to :" + device);
        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptThread != null) {
            btAcceptThread.cancel();
            btAcceptThread = null;
        }

        btConnectingThread =new ConnectingThread(device);
        btConnectingThread.start();

    }

    public synchronized void stop() {
        Log.d(TAG, "STOP ALL THREADS.");

        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptThread != null) {
            btAcceptThread.cancel();
            btAcceptThread = null;
        }
        btState =STATE_STOP;

    }

    public void BTWrite(byte[] out) {
        if (btState != STATE_CONNECTED) return;
        btConnectedThread.write(out);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket BTSereverSocket;
        BluetoothServerSocket tempSocket;
        BluetoothSocket btSocket;
        private BluetoothDevice device;

        public AcceptThread() {
            try {
                tempSocket = btAdapert.listenUsingInsecureRfcommWithServiceRecord("BT_Chat", UUID_String);
                Log.d(TAG, "GET BT SERVERSOCKET OK!");

            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "GET BT SERVERSOCKET FAILED!");
            }
            BTSereverSocket = tempSocket;
            btState = STATE_WaitingConnecting;
        }

        public void run() {

            while (btState != STATE_CONNECTED) {
                try {
                    btSocket = BTSereverSocket.accept();

                } catch (IOException e) {
                    Log.d(TAG, "GET BT Socket fail" + e);
                    break;
                }
            }
            if (btSocket != null) {
                switch (btState) {
                    case STATE_WaitingConnecting:
                    case STATE_CONECTING:
                        device = btSocket.getRemoteDevice();

                        if (btConnectingThread != null) {
                            btConnectingThread.cancel();
                            btConnectingThread = null;
                        }
                        if (btConnectedThread != null) {
                            btConnectedThread.cancel();
                            btConnectedThread = null;
                        }
                        if (btAcceptThread != null) {
                            btAcceptThread.cancel();
                            btAcceptThread = null;

                        }
                        btConnectedThread = new ConnectedThread(btSocket);
                        btConnectedThread.start();

                        Message msg = btHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.DEVICE_NAME, device.getName());
                        msg.setData(bundle);
                        btHandler.sendMessage(msg);
                        break;

                    case STATE_NORMAL:
                    case STATE_CONNECTED:
                        try {
                            btSocket.close();


                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        break;
                }
            }

        }

        public void cancel() {
            try {
                BTSereverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket btSocket;
        private final InputStream btInputStream;
        private final OutputStream btOutputStream;

        public ConnectedThread(BluetoothSocket socket) {
            btSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpout = null;

            try {
                tmpIn = socket.getInputStream();
                tmpout = socket.getOutputStream();


            } catch (IOException e) {
                e.printStackTrace();
            }

            btInputStream = tmpIn;
            btOutputStream = tmpout;
            btState = STATE_CONNECTED;

        }

        public void run() {
            byte[] buffer = new byte[1024];
            int byteReadLength;

            while (btState == STATE_CONNECTED) {
                try {
                    byteReadLength = btInputStream.read(buffer);
                    btHandler.obtainMessage(Constants.MESSAGE_READ, byteReadLength, -1, buffer).sendToTarget();


                } catch (IOException e) {
                    Message msg = btHandler.obtainMessage(Constants.MESSAGE_TOAST);
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.TOAST, "Decice connection is lost.");
                    msg.setData(bundle);
                    btHandler.sendMessage(msg);

                    if (btState != STATE_STOP) {
                        btState = STATE_NORMAL;
                        serverStart();

                    }
                    break;
                }


            }


        }

        public void write(byte[] buffer) {
            try {
                btOutputStream.write(buffer);

            } catch (IOException e) {

            }


        }

        public void cancel() {
            try {
                btSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private class ConnectingThread extends Thread {
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public ConnectingThread(BluetoothDevice device) {
            btDevice = device;
            BluetoothSocket tmpSocket = null;

            try {
                tmpSocket = device.createInsecureRfcommSocketToServiceRecord(UUID_String);

            } catch (IOException e) {
                e.printStackTrace();
            }
            btSocket = tmpSocket;
            btState = STATE_CONECTING;

        }

        public void run() {
            try {
                btSocket.connect();

            } catch (IOException e) {
                e.printStackTrace();
                try {
                    btSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                Message msg = btHandler.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST, "Unable to connect device");
                msg.setData(bundle);
                btHandler.sendMessage(msg);

                if (btState != STATE_STOP) {
                    btState = STATE_NORMAL;
                    serverStart();

                }
                return;
            }
            if (btConnectedThread != null) {
                btConnectedThread.cancel();
                btConnectedThread = null;
            }
            if (btAcceptThread != null) {
                btAcceptThread.cancel();
                btAcceptThread = null;

            }
            btConnectedThread = new ConnectedThread(btSocket);
            btConnectedThread.start();

            Message msg = btHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.DEVICE_NAME, btDevice.getName());
            msg.setData(bundle);
            btHandler.sendMessage(msg);
        }

        public void cancel() {
            try {
                btSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
