package com.wklabs.air_monitor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

//    private TextView receiveText;
//    private TextView sendText;

    private TextView statusText;
    private TextView currDustTextView;
    private TextView modeTextView;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private boolean once  = true;
    private final String[] mode = {"Off","Auto", "Low", "Medium", "High"};

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);


//        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        statusText = view.findViewById(R.id.ConnStatusText);

//        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
//        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

//        sendText = view.findViewById(R.id.send_text);
//        hexWatcher = new TextUtil.HexWatcher(sendText);
//        hexWatcher.enable(hexEnabled);
//        sendText.addTextChangedListener(hexWatcher);
//        sendText.setHint(hexEnabled ? "HEX mode" : "");

        currDustTextView = view.findViewById(R.id.currDustTextView);
        modeTextView = view.findViewById(R.id.ModeStatusText);

//        View sendBtn = view.findViewById(R.id.send_btn);

        View offBtn = view.findViewById(R.id.BtnOff);
        View autoBtn = view.findViewById(R.id.BtnAuto);
        View lowBtn = view.findViewById(R.id.BtnLow);
        View medBtn = view.findViewById(R.id.BtnMid);
        View highBtn = view.findViewById(R.id.BtnHigh);

        offBtn.setOnClickListener(v -> send("0"));
        autoBtn.setOnClickListener(v -> send("1"));
        lowBtn.setOnClickListener(v -> send("2"));
        medBtn.setOnClickListener(v -> send("3"));
        highBtn.setOnClickListener(v -> send("4"));
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
//        inflater.inflate(R.menu.menu_terminal, menu);
//        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
////        if (id == R.id.clear) {
////            receiveText.setText("");
//            return true;
//        } else if (false) {
//            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
//            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
//            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
//            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//            builder.setTitle("Newline");
//            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
//                newline = newlineValues[item1];
//                dialog.dismiss();
//            });
//            builder.create().show();
//            return true;
//        } else if (false) {
//            hexEnabled = !hexEnabled;
////            sendText.setText("");
//            hexWatcher.enable(hexEnabled);
////            sendText.setHint(hexEnabled ? "HEX mode" : "");
//            item.setChecked(hexEnabled);
//            return true;
//        } else {
//            return super.onOptionsItemSelected(item);
//        }
//    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "기기와 연결되지 않았습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
//            currDustTextView.setText(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n');
            } else {


                String msg = new String(data);
                if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    // don't show CR as ^M if directly before LF
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    // special handling if CR and LF come in separate fragments
                    if (pendingNewline && msg.charAt(0) == '\n') {
                        if(spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                        } else {
//                            Editable edt = receiveText.getEditableText();
//                            if (edt != null && edt.length() >= 2)
//                                edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';

                    if(msg.length()>6){
                        System.out.println(msg.length());
                        System.out.println(msg);

                        String recvdMode = msg.split(",")[0];
                        modeTextView.setText(mode[Integer.parseInt(recvdMode)]);

                        String particle = msg.split(",")[1];
                        float particleFloat  = Float.parseFloat(particle.replace("\n",""));
                        currDustTextView.setText(particle.replace("\n",""));

                        if(particleFloat < 15.0){
                            currDustTextView.setTextColor(getResources().getColor(R.color.particleVeryGood));
                        }
                        else if(particleFloat < 35.0){
                            currDustTextView.setTextColor(getResources().getColor(R.color.particleGood));
                        }
                        else if(particleFloat < 105.0){
                            currDustTextView.setTextColor(getResources().getColor(R.color.particleNone));
                        }
                        else if(particleFloat < 150.0){
                            currDustTextView.setTextColor(getResources().getColor(R.color.particleBad));
                        }
                        else {
                            currDustTextView.setTextColor(getResources().getColor(R.color.particleVeryBad));
                        }





                    }

                }
                spn.append(TextUtil.toCaretString(msg, newline.length() != 0));
//                receiveText.append(msg);

//                currDustTextView.setText(TextUtil.toCaretString(msg, newline.length() >1));


            }


        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        receiveText.append(spn);
        statusText.setText(str);
        if(str == "connected"){
            statusText.setTextColor(getResources().getColor(R.color.colorConnectedText));
        }
        else if(str == "connecting..."){
            statusText.setTextColor(getResources().getColor(R.color.colorConnectingText));
        }
        else{
            statusText.setTextColor(getResources().getColor(R.color.colorDisconnectedText));

        }
//        currDustTextView.setText(spn);


    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        statusText.setTextColor(getResources().getColor(R.color.colorConnectedText));

    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        statusText.setTextColor(getResources().getColor(R.color.colorDisconnectedText));

        Toast.makeText(getActivity(), "기기와 연결에 실패했습니다. 다시 연결을 시도합니다.", Toast.LENGTH_LONG).show();
        connect();
//        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        statusText.setTextColor(getResources().getColor(R.color.colorDisconnectedText));

        Toast.makeText(getActivity(), "기기와 연결이 종료되었습니다. 다시 연결을 시도합니다.", Toast.LENGTH_SHORT).show();
        connect();

//
//
//        if(once){
//            connect();
//            once = false;
//        }
//        else{
//            disconnect();
//
//        }


    }

}
