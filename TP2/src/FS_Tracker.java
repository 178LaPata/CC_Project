import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FS_Tracker {

    public static void main(String[] args) {

        ServerSocket tracker = null;

        try {

            tracker = new ServerSocket(9090);

            System.out.println("Servidor ativo em : " + InetAddress.getLocalHost().getHostAddress() + " porta " + tracker.getLocalPort());


            ConcurrentHashMap<String,List<FileInfo>> node_files = new ConcurrentHashMap<>();
            ConcurrentHashMap<String,List<String>> file_Locations = new ConcurrentHashMap<>();


            while (true) {

                Socket node = tracker.accept();

                System.out.println("Novo nodo conectado: " + node.getInetAddress().getHostAddress());

                NodeHandler nodeHandler = new NodeHandler(node,node_files,file_Locations);

                new Thread(nodeHandler).start();
            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (tracker != null) {
                try {
                    tracker.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // ClientHandler class
    private static class NodeHandler implements Runnable {
        private final Socket nodeSocket;
        private ConcurrentHashMap<String,List<FileInfo>> node_files;
        private ConcurrentHashMap<String,List<String>> file_locations;

        public NodeHandler(Socket socket, ConcurrentHashMap<String,List<FileInfo>> node_files, ConcurrentHashMap<String,List<String>> file_locations)
        {
            this.nodeSocket = socket;
            this.node_files = node_files;
            this.file_locations = file_locations;
        }


        public void run()
        {

            try {

                String ip_node = nodeSocket.getInetAddress().getHostAddress();

                DataOutputStream out = new DataOutputStream(nodeSocket.getOutputStream());
                DataInputStream in = new DataInputStream(nodeSocket.getInputStream());



                boolean loop = true;

                while (loop) {

                    int length = in.readInt();

                    System.out.println(length);

                    byte[] received = new byte[length];
                    in.readFully(received,0,length);

                    byte choice = received[0];

                    switch (choice){

                        case 1: {

                            FileInfo fileInfo = FileInfo.deserialize(received);

                            if (!node_files.containsKey(ip_node)){
                                List<FileInfo> fileInfoList = new ArrayList<>();
                                fileInfoList.add(fileInfo);
                                node_files.put(ip_node,fileInfoList);
                            }
                            else {
                                node_files.get(ip_node).add(fileInfo);
                            }

                            if (!file_locations.containsKey(fileInfo.getNome())){
                                List<String> locations = new ArrayList<>();
                                locations.add(ip_node);
                                file_locations.put(fileInfo.getNome(),locations);
                            }
                            else {
                                file_locations.get(fileInfo.getNome()).add(ip_node);
                            }


                            break;

                        }

                        case 2: {

                            List<String> file_names = new ArrayList<>(file_locations.keySet());

                            String message_buffer = String.join(" ",file_names);

                            System.out.println(message_buffer);

                            byte[] message = message_buffer.getBytes();

                            out.writeInt(message.length);
                            out.write(message);
                            out.flush();

                            break;
                        }

                        case 3:{

                            System.out.println("GET");

                            String file_name = new String(received, StandardCharsets.UTF_8).substring(1);

                            List<String> ips = new ArrayList<>();

                            if (file_locations.containsKey(file_name)){
                                ips = file_locations.get(file_name);
                            }


                            out.writeInt(ips.size());

                            
                            out.flush();

                            if (!ips.isEmpty()) {
                                for (String ip : ips){
                                    List<FileInfo> fileInfoList = node_files.get(ip);
                                    for (FileInfo fileInfo : fileInfoList){
                                        if (fileInfo.getNome().equals(file_name)){
                                            System.out.println(fileInfo);
                                            byte[] fileInfoBytes = fileInfo.serialize();
                                            System.out.println(FileInfo.deserialize(fileInfoBytes)   );
                                            out.writeInt(fileInfoBytes.length);
                                            out.write(fileInfoBytes);
                                            out.flush();
                                            break;
                                        }
                                    }   
                                }
                            }
                            else break;

                        }

                        case 4:{


                        }

                        /*

                        case "UPDATE": {

                            int size = in.readInt();

                            List<InfoPacket> files = new ArrayList<>();

                            for (int i = 0; i<size; i++){
                                InfoPacket file = (InfoPacket) in.readObject();
                                files.add(file);
                            }

                            node_files.put(ip_node,files);

                            System.out.println(node_files);

                            break;
                        }




                        case "GET":{

                            String file = in.readUTF();

                            for (Map.Entry<String,List<InfoPacket>> entry : node_files.entrySet()){
                                for (InfoPacket name : entry.getValue()){
                                    if (name.getNome().equals(file)){
                                        out.writeUTF(entry.getKey());
                                        out.writeObject(name);
                                        out.flush();
                                        break;
                                    }
                                }
                            }

                            out.writeUTF("end");
                            out.flush();
                            break;

                        }


                         */

                        case 0:{
                            loop = false;
                            break;
                        }





                    }


                }



            }

            catch (IOException e) {
                e.printStackTrace();
            }
            /*
            catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

             */

            finally {
                try {
                    nodeSocket.shutdownInput();
                    nodeSocket.shutdownOutput();
                    nodeSocket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
