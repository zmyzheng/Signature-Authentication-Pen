package edu.columbia.ee.mingyangzheng.signatureauthentication;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.builder.filter.Comparison;
import com.mbientlab.metawear.builder.filter.ThresholdOutput;
import com.mbientlab.metawear.builder.function.Function1;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Debug;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.Logging;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends Activity implements ServiceConnection {

    private static final String LOG_TAG = "freefall";
    private MetaWearBoard mwBoard;
    private Accelerometer accelerometer;
    private GyroBmi160 gyroBmi160;
    private Debug debug;
    private ArrayList<String> logs = new ArrayList<>();
    private ArrayList<String> logsGyro= new ArrayList<>();
    String response = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.logs = new ArrayList<>();
        getApplicationContext().bindService(new Intent(this, BtleService.class), this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.start_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelerometer.acceleration().start();
                accelerometer.start();
               gyroBmi160.angularVelocity();
                gyroBmi160.start();
            }
        });
        findViewById(R.id.stop_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelerometer.stop();
                accelerometer.acceleration().stop();
                gyroBmi160.stop();
                Toast.makeText(getApplicationContext(), Integer.toString(logs.size()), Toast.LENGTH_SHORT).show();

                // Do something in response to sign up butto
                Runnable runnable = new Runnable(){
                    public void run() {

                        try {

                            URL url = new URL("http://160.39.147.185:5000/predict");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");

                            JSONObject postDataParams = new JSONObject();
                            postDataParams.put("tag", "train");


                            StringBuilder sb1 = new StringBuilder();
                            for (String s : logs)
                            {
                                sb1.append(s);
                                sb1.append("\t");
                            }
                            postDataParams.put("payload1", sb1.toString());
                            StringBuilder sb2 = new StringBuilder();
                            for (String s : logsGyro)
                            {
                                sb2.append(s);
                                sb2.append("\t");
                            }
                            postDataParams.put("payload2", sb2.toString());
                            logs.clear();
                            logsGyro.clear();
                            conn.setRequestProperty("accept", "*/*");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("Accept", "application/json");
                            conn.setRequestProperty("connection", "Keep-Alive");
                            conn.setRequestProperty("user-agent","Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
                            conn.setDoOutput(true);
                            conn.setDoInput(true);

                            OutputStream os = conn.getOutputStream();
                            os.write(postDataParams.toString().getBytes());
                            os.close();
                            // read the response
                            InputStream in = new BufferedInputStream(conn.getInputStream());
                            response = NetworkHandler.convertStreamToString(in);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), response, Toast.LENGTH_SHORT).show();
                                }
                            });

                            Log.v(LOG_TAG, response);

                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Exception: " + e.getMessage());
                        }

                    }
                };
                new Thread(runnable).start();



            }
        });
//        findViewById(R.id.reset_board).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                debug.resetAsync();
//            }
//        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getApplicationContext().unbindService(this);
    }



    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        BtleService.LocalBinder serviceBinder = (BtleService.LocalBinder) service;

        String mwMacAddress= "DB:05:39:BB:76:60";   ///< Put your board's MAC address here
        BluetoothManager btManager= (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothDevice btDevice= btManager.getAdapter().getRemoteDevice(mwMacAddress);

        mwBoard= serviceBinder.getMetaWearBoard(btDevice);
        mwBoard.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {
                accelerometer = mwBoard.getModule(Accelerometer.class);
                accelerometer.configure()
                        .odr(50f)
                        .range(2f)      // Set data range to +/-2g, or closet valid range
                        .commit();
                return accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i(LOG_TAG, data.value(Acceleration.class).toString());
                                logs.add(data.value(Acceleration.class).toString());
                            }
                        });
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                if (task.isFaulted()) {
                    Log.e(LOG_TAG, mwBoard.isConnected() ? "Error setting up route" : "Error connecting", task.getError());
                } else {
                    Log.i(LOG_TAG, "Connected");
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    debug = mwBoard.getModule(Debug.class);
                }

                return null;
            }
        });

        mwBoard.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {
                gyroBmi160 = mwBoard.getModule(GyroBmi160.class);
                // set the data rat to 50Hz and the
                // data range to +/- 2000 degrees/s
                gyroBmi160.configure()
                        .odr(GyroBmi160.OutputDataRate.ODR_50_HZ)
                        .range(GyroBmi160.Range.FSR_2000)
                        .commit();
                return gyroBmi160.angularVelocity().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object ... env) {
                                Log.i("MainActivity", data.value(AngularVelocity.class).toString());
                                logsGyro.add(data.value(AngularVelocity.class).toString());
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}