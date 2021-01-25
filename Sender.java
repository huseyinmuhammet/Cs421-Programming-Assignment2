/*
7.12.2020 Huseyin Gorgulu
This program will send specified file to a server by using UDP. DatagramSocket and DatagramPacket libraries were used.
Packages were prepared for socket to send by arranging size of raw data and arranging sequeceNumber. These data were combined as byte array.
When sending packages concurrently it receives acknowledgements.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.LinkedList;
public class Sender {
    //Global Variables, so thread can access these variables
    public static final int rawPacketSize = 1022;//Only Data
    public static  int packetSize = 1024;//Data with ack bytes
    public static String filePath;
    public static int receiverPort;
    public static int windowSizeN;
    public static String reTransTimeout;
    public static long reTransTimeoutN;
    public static long numberPackets;
    public static long remainder;//For last packet raw datasize
    public static long availableWindow;
    public static FileInputStream fileInput;
    public static int base = 1;
    public static int nextSeqNum = 1;
    public static DatagramSocket s;
    public static DatagramPacket dataPacket;
    public static boolean timeout;
    public static Timer timer;
    public static LinkedList<byte[]> imageListBuff;//image list stored for retransmit if necassary
    public static LinkedList<Integer> noListBuff;//nextseq list stored for retransmit if necassary
    public static SocketAddress sockaddr;
    public static Date time;
    public static Sending send;
    public static Boolean check = true;

    /*
    Sending Thread for transmitting data. Run method is running all times if otherwise specified
     */
   static class Sending extends Thread
    {
        public void run()
        {
            while(true) {
                System.out.println(base + " :b-n: " + nextSeqNum);//without this line thread wont update the values of base and nextseqnum

                    if(nextSeqNum < base + windowSizeN){
                        byte [] b;
                        if(nextSeqNum  == numberPackets ){//Last image packet
                            b = new byte[(int) remainder];
                            try {
                                fileInput.read(b);
                            }catch (IOException e) {
                                e.printStackTrace();
                            }
                            // Array list that won lose when there is a timeout
                            imageListBuff.add(b);
                            noListBuff.add(nextSeqNum);
                            packetSize = (int) remainder+2 ;
                        }
                        else if(nextSeqNum < numberPackets){//image packets
                            b = new byte[rawPacketSize];
                            // Reading data from file a packet
                            try {
                                fileInput.read(b);
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                            imageListBuff.add(b);
                            noListBuff.add(nextSeqNum);
                        }
                        else{//Sending 0 to finish operations
                            nextSeqNum = 0;
                            b = new byte[rawPacketSize];

                            //Converting bytes to int
                            byte[] seq = new byte[2];
                            seq[1] = (byte) (nextSeqNum & 0xFF);
                            seq[0] = (byte) ((nextSeqNum >> 8) & 0xFF);
                            nextSeqNum = 0;
                            dataPacket = new DatagramPacket(seq, 0, 2, sockaddr);

                            //Send last data
                            try {
                                s.send(dataPacket);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            System.exit(1);
                            break;
                        }
                        //Converting bytes to int
                        byte[] seq = new byte[2];
                        seq[1] = (byte) (nextSeqNum & 0xFF);
                        seq[0] = (byte) ((nextSeqNum >> 8) & 0xFF);
                        //Combining bytes
                        byte[] combined = new byte[seq.length + b.length];
                        System.arraycopy(seq, 0, combined, 0, seq.length);
                        System.arraycopy(b, 0, combined, seq.length, b.length);
                        dataPacket = new DatagramPacket(combined, 0, packetSize, sockaddr);
                        try {
                            s.send(dataPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        nextSeqNum++;
                    }
               }
            }
        }
    /*
    Receiver Thread that receive acknowledgements if timeout happens retransmit from Base for a window size
     */
    static class ReceiveACK extends Thread
    {
        public void run()throws IllegalStateException {
            while (true){
                try {
                    byte[] buf = new byte[2];
                    DatagramPacket dp = new DatagramPacket(buf, 2);
                    s.setSoTimeout((int) reTransTimeoutN);
                    s.receive(dp);
                    byte[] asd = dp.getData();
                    int ack = (asd[1] & 0xff) + (asd[0]& 0xff) * 256;
                    System.out.println("ACK is " + ack );
                    if(ack > base) {
                        base = ack + 1 ;
                        if (noListBuff.contains(ack)) {
                            int index = noListBuff.indexOf(ack);
                            for (int i = 0; i < index; i++) {
                                imageListBuff.remove(0);
                                noListBuff.remove(0);
                            }
                        }
                    }
                } catch(SocketTimeoutException e) {// If timeout happens
                   // System.out.println("Timeout");
                    // Resending datas due to timeout
                    for (int i = 0; i < noListBuff.size()-1; i++) {
                        byte[] seq = new byte[2];
                        seq[1] = (byte) (noListBuff.get(i) & 0xFF);
                        seq[0] = (byte) ((noListBuff.get(i) >> 8) & 0xFF);
                        byte[] b = imageListBuff.get(i);
                        byte[] combined = new byte[seq.length + b.length];
                        System.arraycopy(seq, 0, combined, 0, seq.length);
                        System.arraycopy(b, 0, combined, seq.length, b.length);
                        dataPacket = new DatagramPacket(combined, 0, packetSize, sockaddr);
                        try {
                            s.send(dataPacket);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
            }
        }
    /*
    Main Method
     */
    public static void main(String[] args) throws FileNotFoundException, SocketException {

        // Inputs
        filePath = args[0];
        String port = args[1];
        receiverPort = Integer.parseInt(port);
        String windowSize = args[2];
        windowSizeN = Integer.parseInt(windowSize);
        reTransTimeout = args[3];
        reTransTimeoutN = Long.parseLong(reTransTimeout);
        availableWindow = windowSizeN;
        File image = new File(filePath);
        timeout = false;
        long sizeFile = image.length();
        // Finding Number of packets in file
        numberPackets = sizeFile / 1022;
        // if file size has remainder from raw data size
        if(sizeFile % 1022 != 0  ){
            numberPackets++;
            remainder = sizeFile % 1022;
        }
        //initializations
        imageListBuff = new LinkedList<byte[]>();
        noListBuff = new LinkedList<Integer>();
        timer = new Timer();
        time = new Date(reTransTimeoutN);
        fileInput = new FileInputStream(filePath);
        s = new DatagramSocket();
        sockaddr = new InetSocketAddress("localhost",receiverPort);
        s.connect(sockaddr);
        //s.setSoTimeout((int) reTransTimeoutN);
        send = new Sending();
        ReceiveACK receive = new ReceiveACK();
        //Starting Threads
        send.start();
        receive.start();
    }
}
