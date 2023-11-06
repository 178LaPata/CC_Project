import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FS_Node {

    public static void main(String[] args) throws IOException {

        try {
            String server_ip = args[1];
            int server_port = Integer.parseInt(args[2]);

            Socket socket = new Socket(server_ip, server_port);

            System.out.println("Conexão FS Track Protocol com servidor " + server_ip + " porta " + server_port);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());


            // Ler file names no folder escolhido para partilhar com tracker
            File folder = new File(args[0]);
            File[] listOfFiles = folder.listFiles();

            if (listOfFiles != null) {

                byte choice = 1;

                //byte[] ip = InetAddress.getLocalHost().toString().getBytes();

                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        File file = listOfFiles[i];
                        byte[] fileInfo = new FileInfo(file.getName(), 3).serialize();

                        System.out.println(file.getName());



                        byte[] combined = new byte[fileInfo.length + 1];

                        System.out.println(combined.length);

                        ByteBuffer buffer_message = ByteBuffer.wrap(combined);
                        buffer_message.put(choice);
                        buffer_message.put(fileInfo);
                        byte[] message = buffer_message.array();

                        System.out.println(message[0]);

                        out.writeInt(message.length);
                        out.write(message);
                        out.flush();
                    }
                /*
                else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
                 */
                }


            }


            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));

            boolean loop = true;

            while(loop){

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]){

                    case "GET":{

                        byte[] choice = ("3" + option[1]).getBytes();
                        out.writeInt(choice.length);
                        out.write(choice);
                        out.flush();

                        if (in.readInt() != 0) {

                            int

                        }


                        ArrayList<String> files = new ArrayList<>();
                        while (true){
                            String ip = in.readUTF();
                            if (ip.equals("end")) break;
                            InfoPacket file = (InfoPacket) in.readObject();
                            files.add(ip);
                        }

                        System.out.println(files);

                        break;

                    }



                    case "FILES":{

                        out.writeInt(1);
                        out.writeByte(2);
                        out.flush();

                        int length = in.readInt();

                        byte[] received = new byte[length];
                        in.readFully(received,0,length);

                        String answer = new String(received, StandardCharsets.UTF_8);

                        System.out.println(answer);

                        break;
                    }

                    case "QUIT":{
                        out.writeInt(1);
                        out.writeByte(0);
                        out.flush();
                        loop = false;

                        break;
                    }

                }


            }



            System.out.println("Terminando Programa...");

            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        } catch (ConnectException e) {
            System.err.println("Conexão ao servidor falhada!");
        } catch (IOException e) {
            e.printStackTrace();


        }

    }

}