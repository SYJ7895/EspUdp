package com.espressif.esptouch.android.v1;

import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class udpserver implements Runnable {

    private DatagramPacket dpRcv = null,dpSend = null;
    private static DatagramSocket ds = null;
    private final byte[] msgRcv = new byte[1024];
    private boolean udpLife = true;     //udp生命线程
//    private boolean udpLifeOver = true; //生命结束标志，false为结束
    private final String  ssid ;
    private final String  pass;
    private final String  serverip;
    private final int  devcount;
    private  String  result = null;
    private  int curNum;
    private  int okNum;
    private String[] SN;

  void interrupt() {
      result = "cancel";
      udpLife = false;
  }
    public udpserver(String ssid,String pass,String serverip,String devcount) {
        this.ssid = ssid;
        this.pass = pass;
        this.serverip = serverip;
        this.devcount = Integer.parseInt(devcount);
        this.SN = new String[this.devcount];
        this.curNum = 0;
        this.okNum = 0;

    }

    private void SetSoTime(int ms) throws SocketException {
        ds.setSoTimeout(ms);
    }

//    //返回udp生命线程因子是否存活
//    public boolean isUdpLife(){
//        if (udpLife){
//            return true;
//        }
//
//        return false;
//    }

//    //返回具体线程生命信息是否完结
//    public boolean getLifeMsg(){
//        return udpLifeOver;
//    }

    //更改UDP生命线程因子
    public void setUdpLife(boolean b){
      if(!b){
          result = "cancel";
      }
      udpLife = b;
    }
//
//    public void Send(String sendStr) throws IOException {
//        Log.i("SocketInfo","客户端IP："+ dpRcv.getAddress().getHostAddress() +"客户端Port:"+ dpRcv.getPort());
//        dpSend = new DatagramPacket(sendStr.getBytes(),sendStr.getBytes().length,dpRcv.getAddress(),dpRcv.getPort());
//        ds.send(dpSend);
//    }

    public String getresult() {
          while (udpLife){
              try {
                  Thread.sleep(2000);//五秒判断一次
              } catch (InterruptedException e) {
                  // TODO Auto-generated catch block
                  //  e.printStackTrace();
              }

          }
          return  result;

    }


    public String getSN(String str, String strStart, String strEnd) {
        String tempStr;
        tempStr = str.substring(str.indexOf(strStart) + 1, str.lastIndexOf(strEnd));
        return tempStr;
    }


    @Override
    public void run() {
        try {
            ds = new DatagramSocket(12345);
            Log.i("SocketInfo", "UDP服务器已经启动");

            SetSoTime(1000);
            //设置超时，不需要可以删除
        } catch (SocketException e) {
            Log.i("SocketInfo", "UDP服务器创建失败");
            e.printStackTrace();
        }

        Intent startint =new Intent();
        startint.setAction("udpReceiver");
        startint.putExtra("udpReceiver","正在配置，等待客户端接入……\r\n");
        EspTouchActivity.context.sendBroadcast(startint);

        dpRcv = new DatagramPacket(msgRcv, msgRcv.length);
        while (udpLife) {
            try {
                Log.i("SocketInfo", "UDP监听中");
                ds.receive(dpRcv);

                String string = new String(dpRcv.getData(), dpRcv.getOffset(), dpRcv.getLength());
                String snName = getSN(string,":","\r\n");
                String sendStr = null;
                if(string.contains("apreq")){
                    if(curNum == 0){
                        SN[0] = snName;
                        curNum = curNum+1;
                    }else{
                        boolean isKnown = false;
                        for(int i = 0;i < curNum;i++){
                            if(SN[i].contains(snName)){
                                isKnown = true;
                                break;
                            }
                        }
                        if(!isKnown){
                            SN[curNum] = snName;
                            curNum = curNum+1;
                        }

                    }
                    sendStr = "ssid:"+ssid+"\r\npassword:"+pass+"\r\n"+"IP:"+serverip+"\r\n";
                    dpSend = new DatagramPacket(sendStr.getBytes(),sendStr.getBytes().length,dpRcv.getAddress(),dpRcv.getPort());
                }else if(string.contains("apok")){
                    for(int i = 0;i < curNum;i++){
                        if(SN[i].contains(snName)){
                            okNum = okNum + 1;
                            SN[i] = "OK";
                            break;
                        }
                    }
                    result = "apok";
                    sendStr = "reboot\r\n";
                    dpSend = new DatagramPacket(sendStr.getBytes(),sendStr.getBytes().length,dpRcv.getAddress(),dpRcv.getPort());
                }else if(string.contains("apfail")){
                    result = "apfail";
                    sendStr = "reboot\r\n";
                    dpSend = new DatagramPacket(sendStr.getBytes(),sendStr.getBytes().length,dpRcv.getAddress(),dpRcv.getPort());
                    udpLife = false;
                }
                if(sendStr != null){
                    ds.send(dpSend);
                }

                Log.i("SocketInfo", "收到信息：" + string);
                Intent intent =new Intent();
                intent.setAction("udpReceiver");
                intent.putExtra("udpReceiver","recvmsg:"+string+"sendmsg:"+sendStr);
                EspTouchActivity.context.sendBroadcast(intent);

                if(okNum == devcount){
                    udpLife = false;
                    break;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        ds.close();
        Log.i("SocketInfo","UDP监听关闭");
        //udp生命结束
//        udpLifeOver = false;
    }
}
