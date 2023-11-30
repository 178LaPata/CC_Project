import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FS_Node {

    public static void main(String[] args) throws IOException {

        try {
            String server_ip = args[1];
            int server_port = Integer.parseInt(args[2]);

            Socket socket = new Socket(server_ip, server_port);

            System.out.println("Conexão FS Track Protocol com servidor " + server_ip + " porta " + server_port);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());



            List<Integer> fds = new ArrayList<>();

            fds.toA

            String[] register = {"REGISTER", args[0]};

            byte[] register_message = Requests.create_request(register);

            //System.out.println(new String(Requests.create_request(fds),StandardCharsets.UTF_8));

            if (register_message != null) {
                out.write(register_message);
                out.flush();
            }


            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));


/*
            boolean loop = true;



            while(loop){

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]){

                    case "GET":{

                        byte choice = 3;

                        byte[] combined = new byte[option[1].length() + 1];
                    
                        ByteBuffer buffer_message = ByteBuffer.wrap(combined);
                        buffer_message.put(choice);
                        buffer_message.put(option[1].getBytes());
                        byte[] message = buffer_message.array();
                        out.writeInt(message.length);
                        out.write(message);
                        out.flush();

                        int size = in.readInt();
                        


                        if (size != 0) {
                            
                            Map<String,FileInfo> fileInfoMap = new HashMap<>();


                            for (int i = 0; i<size; i++){

                                int length = in.readInt();

                                byte[] received = new byte[length];
                                in.readFully(received,0,length);
                                FileInfo fileInfo = FileInfo.deserialize(received);

                                fileInfoMap.put(in.readUTF(),fileInfo);
                            }
                            
                            System.out.println(fileInfoMap);


                        }


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


 */


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