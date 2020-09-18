package vpntosocket.shadowrouter.org.vpntosocket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import vpntosocket.shadowrouter.org.vpntosocket.VPN.VPNService;

public class Main extends Activity implements VPNService.onStatusChangedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        VPNService.addOnStatusChangedListener(this);
        checkRunning();

        Button connect = findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if(VPNService.vpnThread == null){
                    Intent intent = VPNService.prepare(Main.this);
                    if(intent != null){
                        startActivityForResult(intent, 0);
                    }else{
                        onActivityResult(0, RESULT_OK, null);
                    }

                }else{
                    VPNService.vpnThread.interrupt();
                }
            }
        });
    }


    @Override
    protected void onActivityResult(int request, int result, Intent data){
        if(result == RESULT_OK){
            Intent intent = new Intent(this, VPNService.class);
            startService(intent);
        }
    }

    @Override
    protected void onDestroy(){
        VPNService.removeOnStatusChangedListener(this);
        super.onDestroy();
    }

    @Override
    public void onStatusChanged(final boolean status){
        final Button connect = findViewById(R.id.connect);
        if(connect != null){
            connect.post(new Runnable(){
                @Override
                public void run(){
                    connect.setText((status) ? "Disconnect" : "Connect");
                }
            });
        }
    }

    public void checkRunning(){
        boolean status = (VPNService.vpnThread != null && !VPNService.vpnThread.isInterrupted());
        Button connect = findViewById(R.id.connect);
        connect.setText((status) ? "Disconnect" : "Connect");
    }
}
