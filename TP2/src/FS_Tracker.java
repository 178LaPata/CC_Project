import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FS_Tracker {

    public static void main(String[] args) {


        ServerSocket tracker = null;

        try {

            tracker = new ServerSocket(9090);

            //System.out.println("Servidor ativo em : " + InetAddress.getLocalHost().getHostAddress() + " porta " + tracker.getLocalPort());

            ConcurrentHashMap<String, List<FileInfo>> node_files = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<String>> file_Locations = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<byte[]>> file_hash = new ConcurrentHashMap<>();


            while (true) {

                Socket node = tracker.accept();

                InetSocketAddress remoteSocketAddress = (InetSocketAddress) node.getRemoteSocketAddress();
                String ipNode = remoteSocketAddress.getAddress().getHostAddress();

                System.out.println("Novo nodo conectado: " + ipNode);

                NodeHandler nodeHandler = new NodeHandler(node, node_files, file_Locations, file_hash, ipNode);

                new Thread(nodeHandler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tracker != null) {
                try {
                    tracker.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // ClientHandler class
    private static class NodeHandler implements Runnable {

        private final Socket nodeSocket;
        private ConcurrentHashMap<String, List<FileInfo>> node_files;
        private ConcurrentHashMap<String, List<String>> file_locations;
        private ConcurrentHashMap<String, List<byte[]>> file_hash;
        private String ip_node;

        public NodeHandler(Socket socket, ConcurrentHashMap<String, List<FileInfo>> node_files, ConcurrentHashMap<String, List<String>> file_locations, ConcurrentHashMap<String, List<byte[]>> file_hash, String ip_node) {
            this.nodeSocket = socket;
            this.node_files = node_files;
            this.file_locations = file_locations;
            this.file_hash = file_hash;
            this.ip_node = ip_node;
        }


        public void run() {

            try {

                DataOutputStream out = new DataOutputStream(nodeSocket.getOutputStream());
                DataInputStream in = new DataInputStream(nodeSocket.getInputStream());

                boolean loop = true;

                while (loop) {

                    byte choice = in.readByte();

                    switch (choice) {


                        case 1: {

                            byte number_files = in.readByte();

                            System.out.println(choice);


                            for (byte i = 0; i < number_files; i++) {

                                byte size_name = in.readByte();
                                byte[] name_bytes = new byte[size_name];
                                in.readFully(name_bytes, 0, size_name);
                                String name = new String(name_bytes, StandardCharsets.UTF_8);

                                byte[] blocos_quantidade_bytes = new byte[4];
                                in.readFully(blocos_quantidade_bytes, 0, 4);
                                int blocos_quantidade = Serializer.fourBytesToInt(blocos_quantidade_bytes);

                                byte[] size_blocos_disponiveis_bytes = new byte[4];
                                in.readFully(size_blocos_disponiveis_bytes, 0, 4);
                                int size_blocos_disponiveis = Serializer.fourBytesToInt(size_blocos_disponiveis_bytes);

                                FileInfo fileInfo;
                                byte[] sha1Encoding = null;
                                if (size_blocos_disponiveis == 0) {
                                    fileInfo = new FileInfo(name, blocos_quantidade);
                                    sha1Encoding = new byte[20 * blocos_quantidade];
                                    in.readFully(sha1Encoding, 0, 20 * blocos_quantidade);
                                } else {
                                    Set<Integer> blocos = new HashSet<>();
                                    for (int b = 0; b < size_blocos_disponiveis; b++) {
                                        byte[] bloco_buffer = new byte[4];
                                        in.readFully(bloco_buffer, 0, 4);
                                        blocos.add(Serializer.fourBytesToInt(bloco_buffer));
                                    }
                                    fileInfo = new FileInfo(name, blocos_quantidade, blocos);
                                }

                                if (!node_files.containsKey(ip_node)) {
                                    List<FileInfo> fileInfoList = new ArrayList<>();
                                    fileInfoList.add(fileInfo);
                                    node_files.put(ip_node, fileInfoList);
                                } else {
                                    node_files.get(ip_node).add(fileInfo);
                                }

                                if (!file_locations.containsKey(fileInfo.getNome())) {
                                    List<String> locations = new ArrayList<>();
                                    locations.add(ip_node);
                                    file_locations.put(fileInfo.getNome(), locations);
                                } else {
                                    file_locations.get(fileInfo.getNome()).add(ip_node);
                                }

                                if (sha1Encoding != null) {
                                    if (!file_hash.containsKey(name)) {
                                        List<byte[]> tempList = new ArrayList<>();
                                        for (int id = 0; id < blocos_quantidade; id++) {
                                            byte[] tempByte = new byte[20];
                                            System.arraycopy(tempByte, 0, sha1Encoding, id * 20, 20);
                                            tempList.add(tempByte);
                                        }
                                        file_hash.put(name, tempList);
                                    }
                                }

                                System.out.println(file_hash);

                            }


                            break;
                        }


                        //UPDATE
                        //read tpmanager.getupdatemesage
                        case 2: {

                            byte sizeName = in.readByte();
                            byte[] nameBytes = new byte[sizeName];
                            in.readFully(nameBytes, 0, sizeName);
                            String name = new String(nameBytes, StandardCharsets.UTF_8);
                            int bloco = in.readInt();

                            List<String> locations = file_locations.get(name);

                            if (!locations.contains(ip_node)) {
                                locations.add(ip_node);

                                Set<Integer> blocos_disponiveis = new HashSet<>();
                                blocos_disponiveis.add(bloco);
                                FileInfo fileInfo = new FileInfo(name, file_hash.get(name).size(), blocos_disponiveis);
                                if (node_files.get(ip_node) == null)
                                    node_files.put(ip_node,new ArrayList<>());
                                node_files.get(ip_node).add(fileInfo);
                            } else {
                                List<FileInfo> fileInfoList = node_files.get(ip_node);
                                for (FileInfo fileInfo : fileInfoList) {
                                    if (fileInfo.getNome().equals(name)) {
                                        fileInfo.addBlocoDisponivel(bloco);
                                        break;
                                    }
                                }
                            }


                            break;
                        }


                        case 3: {
                            byte[] message = TPManager.filesAvailableMessage(file_locations.keySet());
                            out.write(message);
                            out.flush();
                            break;
                        }


                        //GET file
                        case 4: {
                            byte size_fileName = in.readByte();
                            byte[] fileName_bytes = new byte[size_fileName];
                            in.readFully(fileName_bytes, 0, size_fileName);
                            String fileName = new String(fileName_bytes, StandardCharsets.UTF_8);
                            List<String> ips = file_locations.get(fileName);

                            if (ips == null) {
                                out.writeByte(0);
                                out.flush();
                                break;
                            }

                            int nodos_totais = ips.size();
                            int blocos_quantidade;
                            List<byte[]> hashes = new ArrayList<>();
                            int size_mensagem = 0;
                            List<byte[]> nodo_mensagens = new ArrayList<>();


                            blocos_quantidade = file_hash.get(fileName).size();

                            //Go to filehash and copy each byte[] to hashes
                            for (byte[] hash : file_hash.get(fileName)){
                                hashes.add(hash);
                                System.out.println(Arrays.toString(hash));
                            }

                            for (String ip : ips) {

                                List<FileInfo> fileInfoList = node_files.get(ip);
                                FileInfo fileInfo = null;
                                for (FileInfo f : fileInfoList)
                                    if (f.getNome().equals(fileName)) {
                                        fileInfo = f;
                                        break;
                                    }


                                if (fileInfo.complete) {
                                    ByteBuffer byteBufferTemp = ByteBuffer.allocate(4 + 4);
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    byteBufferTemp.put(ipBytes);
                                    byte[] number_blocks = new byte[4];
                                    byteBufferTemp.put(number_blocks);
                                    byte[] msg = byteBufferTemp.array();
                                    nodo_mensagens.add(msg);
                                    size_mensagem += msg.length;
                                } else {
                                    Set<Integer> fds = fileInfo.blocos_disponiveis;
                                    ByteBuffer byteBufferTemp = ByteBuffer.allocate(4 + 4 + 4 * fds.size());
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    byteBufferTemp.put(ipBytes);
                                    byteBufferTemp.put(Serializer.intToFourBytes(fds.size()));
                                    for (int a : fds) {
                                        byteBufferTemp.put(Serializer.intToFourBytes(a));
                                    }
                                    byte[] msg = byteBufferTemp.array();
                                    size_mensagem += msg.length;
                                    nodo_mensagens.add(msg);
                                }

                            }

                            ByteBuffer byteBuffer = ByteBuffer.allocate(2 + 4 + hashes.size() * 20 + size_mensagem);

                            byteBuffer.put(Serializer.intToTwoBytes(nodos_totais));
                            byteBuffer.put(Serializer.intToFourBytes(blocos_quantidade));
                            for (byte[] bytes : hashes) {
                                byteBuffer.put(bytes);
                            }
                            for (byte[] bytes : nodo_mensagens)
                                byteBuffer.put(bytes);

                            out.write(byteBuffer.array());
                            out.flush();

                            break;
                        }

                        case 5: {
                            byte size_fileName = in.readByte();
                            byte[] fileName_bytes = new byte[size_fileName];
                            in.readFully(fileName_bytes, 0, size_fileName);
                            String fileName = new String(fileName_bytes, StandardCharsets.UTF_8);
                            byte[] blocoBytes = new byte[4];
                            in.readFully(blocoBytes, 0, 4);
                            int bloco = Serializer.fourBytesToInt(blocoBytes);
                            out.write(file_hash.get(fileName).get(bloco));
                            out.flush();
                            break;
                        }


                        //check if node still online
                            //the message recieved is checkNodemMessage
                        case 8: {
                            //get checkNodeMessage
                            //in.read 4 bytes
                            byte[] message = new byte[4];
                            in.readFully(message, 0, 4);
                            //byte[4] to ip
                            String ip = InetAddress.getByAddress(message).getHostAddress();
                            if (node_files.containsKey(ip)) {
                                out.writeByte(1);
                                out.flush();
                            } else {
                                out.writeByte(0);
                                out.flush();
                            }
                            break;
                        }

                        case 0: {
                            System.out.println("Conexão fechada com nodo: " + nodeSocket.getInetAddress().getHostAddress());
                            loop = false;
                            //Check if node is in node_files
                            //If it is, remove it and remove it's files from file_locations
                            //Also, if file from file_locations has 0 elements, remove it
                            if (node_files.containsKey(ip_node)) {
                                List<FileInfo> fileInfoList = node_files.remove(ip_node);
                                for (FileInfo fileInfo : fileInfoList) {
                                    List<String> locations = file_locations.get(fileInfo.getNome());
                                    locations.remove(ip_node);
                                    if (locations.isEmpty()) {
                                        file_locations.remove(fileInfo.getNome());
                                    }
                                }
                            }
                            break;
                        }


                    }


                }




                /*
                byte[] choice_read = new byte[1];
                in.readFully(choice_read,0,1);


                System.out.println(new String(choice_read,StandardCharsets.UTF_8));

                byte[] size_size = new byte[1];
                in.readFully(size_size,0,1);

                byte sizefds = size_size[0];

                byte[] size = new byte[sizefds];
                in.readFully(size,0,sizefds);

                int sizemano = Integer.getInteger(new String(size,StandardCharsets.UTF_8));


                byte[] mssg = new byte[sizemano];
                in.readFully(mssg,0,sizemano);


                System.out.println(Arrays.toString(mssg));

                 */


                /*
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




                        case 0:{
                            loop = false;
                            break;
                        }





                    }


                }

                */


            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    nodeSocket.shutdownInput();
                    nodeSocket.shutdownOutput();
                    nodeSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public static String generateRandomIp() {
        Random random = new Random();
        StringBuilder ipAddress = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            ipAddress.append(random.nextInt(256));

            if (i < 3) {
                ipAddress.append(".");
            }
        }

        return ipAddress.toString();
    }

}
