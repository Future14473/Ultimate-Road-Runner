package org.firstinspires.ftc.teamcode.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothClient {

	private static final int REQUEST_ENABLE_BT = 1;

	private final BluetoothAdapter btAdapter;
	private BluetoothSocket btSocket = null;

	private OutputStream outStream = null;
	private BufferedReader bReader;

	// SPP UUID
	private final UUID MY_UUID;
	// Server's MAC address
	private final String address;

	Telemetry out;
	Activity activity;

	private List<responseListener> listeners = new ArrayList<>();
	public void addResponseListener(responseListener r) {listeners.add(r);}

	volatile boolean active = false;
	private final Thread listen = new Thread(()->{
		while(true) {
			if(!active)
				continue;
			String lineRead;
			try {
				lineRead = bReader.readLine();
			} catch (IOException e) {
				outAppend("<System> Connection closed by client");
				active = false;
				endHostSession();
				continue;
			}
			for(responseListener l : listeners)
				l.responseReceived(lineRead);
			outAppend("<Host> " + lineRead);
		}
	});

	public void outAppend(String s){
		Log.d("Log", s);
		out.addData("Log",s);
		out.update();
	}

	public BluetoothClient(Activity activity, String address, String uuid, Telemetry telemetry, responseListener rl) {
		this.address = address;
		this.MY_UUID = UUID.fromString(uuid);
		this.activity = activity;
		this.out = telemetry;
		addResponseListener(rl);

		btAdapter = BluetoothAdapter.getDefaultAdapter();
		CheckBTState();

		listen.start();
	}

	public void startHostSession() {
		// Set up a pointer to the remote node using it's address.
		BluetoothDevice device = btAdapter.getRemoteDevice(address);

		outAppend("<System> Connecting to " + device.getName() + " at " + address);

		// Two things are needed to make a connection:
		//	 A MAC address, which we got above.
		//	 A Service ID or UUID.	 In this case we are using the
		//	 UUID for SPP.
		try {
			btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
		} catch (IOException e) {
			outAppend("<Error> Socket create failed");
		}

		// Discovery is resource intensive.	 Make sure it isn't going on
		// when you attempt to connect and pass your message.
		btAdapter.cancelDiscovery();

		// Establish the connection. This will block until it connects.
		try {
			btSocket.connect();
			outAppend("<System> Connection established");
		} catch (IOException e) {
			try {
				btSocket.close();
			} catch (IOException e2) {
				outAppend("<Error> Unable to close socket during connection failure");
			}
		}

		// Create a data stream so we can talk to server.
		try {
			outStream = btSocket.getOutputStream();
			bReader = new BufferedReader(
					new InputStreamReader(
							btSocket.getInputStream()));
		} catch (IOException e) {
			outAppend("<Error> IO stream creation failed");
		}

		active = true;

		send("Hello from Client\n");
	}

	public void send(String s){
		if(!active){
			outAppend("<System> Connect to a host first");
			return;
		}
		byte[] msgBuffer = s.getBytes();
		try {
			outStream.write(msgBuffer);
		}catch (IOException e){
			if (address.equals("00:00:00:00:00:00"))
				outAppend("<Error> Send fail. Server address is 00:00:00:00:00:00");
			else
				outAppend("<Error> Send fail. Check that a device with the SPP UUID " + MY_UUID.toString() + " is paired");
			e.printStackTrace();
		}
	}

	public void endHostSession() {
		outAppend("<System> End session");

		if (outStream != null) {
			try {
				outStream.flush();
			} catch (IOException e) {
				outAppend("<Error> Couldn't end session");
			}
		}

		try	{
			btSocket.close();
		} catch (IOException e2) {
			outAppend("<Error> Couldn't end session");
		}
	}


	private void CheckBTState() {
		// Check for Bluetooth support and then check to make sure it is turned on

		// Emulator doesn't support Bluetooth and will return null
		if(btAdapter==null) {
			outAppend("<Error> Bluetooth not supported");
		} else {
			if (!btAdapter.isEnabled()) {
				//Prompt user to turn on Bluetooth
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
	}

	public interface responseListener {
		void responseReceived(String reponse);
	}

}