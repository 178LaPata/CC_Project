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

            System.out.println("Servidor ativo em : " + InetAddress.getLocalHost().getHostAddress() + " porta " + tracker.getLocalPort());


            ConcurrentHashMap<String, List<FileInfo>> node_files = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<String>> file_Locations = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, List<byte[]>> file_hash = new ConcurrentHashMap<>();


            while (true) {

                Socket node = tracker.accept();

                System.out.println("Novo nodo conectado: " + node.getInetAddress().getHostAddress());

                NodeHandler nodeHandler = new NodeHandler(node, node_files, file_Locations, file_hash);

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


        public NodeHandler(Socket socket, ConcurrentHashMap<String, List<FileInfo>> node_files, ConcurrentHashMap<String, List<String>> file_locations, ConcurrentHashMap<String, List<byte[]>> file_hash) {
            this.nodeSocket = socket;
            this.node_files = node_files;
            this.file_locations = file_locations;
            this.file_hash = file_hash;
        }


        public void run() {

            try {

                String ip_node = nodeSocket.getInetAddress().getHostAddress();

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
                                    sha1Encoding = new byte[20*blocos_quantidade];
                                    in.readFully(sha1Encoding,0,20*blocos_quantidade);
                                } else {
                                    List<Integer> blocos = new ArrayList<>();
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

                                if (sha1Encoding != null){
                                    if (!file_hash.containsKey(name)){
                                        List<byte[]> tempList = new ArrayList<>();
                                        for (int id = 0; id<blocos_quantidade; id++){
                                            byte[] tempByte = new byte[20];
                                            System.arraycopy(tempByte,0,sha1Encoding,id*20,20);
                                            tempList.add(tempByte);
                                        }
                                        file_hash.put(name,tempList);
                                    }
                                }

                                System.out.println(node_files);

                            }



                            break;
                        }


                        //UPDATE
                            //read tpmanager.getupdatemesage
                        case 2: {
                            byte sizeName = in.readByte();
                            byte[] nameBytes = new byte[sizeName];
                            in.readFully(nameBytes,0,sizeName);
                            int bloco = in.readInt();

                            //add location to file_locations if it doesnt exist already
                            //assume key exists
                            String fileName = new String(nameBytes,StandardCharsets.UTF_8);
                            List<String> locations = file_locations.get(fileName);
                            if (!locations.contains(ip_node))
                                locations.add(ip_node);


                            if (node_files.get(ip_node)==null){
                                List<FileInfo> fileInfoList = new ArrayList<>();
                                FileInfo fileInfo = new FileInfo(fileName,1);
                                node_files.put(ip_node,fileInfoList);
                            }



                            ConcurrentHashMap<String, List<FileInfo>> node_files = new ConcurrentHashMap<>();
                        }



                        case 3:{
                            byte[] message = TPManager.filesAvailableMessage(file_locations.keySet());
                            out.write(message);
                            out.flush();
                            break;
                        }


                        //GET file
                        case 4:{
                            byte size_fileName = in.readByte();
                            byte[] fileName_bytes = new byte[size_fileName];
                            in.readFully(fileName_bytes,0,size_fileName);
                            String fileName = new String(fileName_bytes,StandardCharsets.UTF_8);
                            List<String> ips = file_locations.get(fileName);

                            if (ips == null){
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
                            for (int i = 0; i<blocos_quantidade; i++){
                                byte[] tempByte = new byte[20];
                                System.arraycopy(tempByte,0,file_hash.get(fileName).get(i),0,20);
                                hashes.add(tempByte);
                            }

                            for (String ip : ips){

                                List<FileInfo> fileInfoList = node_files.get(ip);
                                FileInfo fileInfo = null;
                                for (FileInfo f : fileInfoList)
                                    if (f.getNome().equals(fileName)) {
                                        fileInfo = f;
                                        break;
                                    }


                                if (fileInfo.complete){
                                    ByteBuffer byteBufferTemp = ByteBuffer.allocate(4+4);
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    byteBufferTemp.put(ipBytes);
                                    byte[] number_blocks = new byte[4];
                                    byteBufferTemp.put(number_blocks);
                                    byte[] msg = byteBufferTemp.array();
                                    nodo_mensagens.add(msg);
                                    size_mensagem += msg.length;
                                }
                                else{
                                    List<Integer> fds = fileInfo.blocos_disponiveis;
                                    ByteBuffer byteBufferTemp = ByteBuffer.allocate(4+4+4*fds.size());
                                    byte[] ipBytes = InetAddress.getByName(ip).getAddress();
                                    byteBufferTemp.put(ipBytes);
                                    byteBufferTemp.put(Serializer.intToFourBytes(fds.size()));
                                    for (int a : fds){
                                        byteBufferTemp.put(Serializer.intToFourBytes(a));
                                    }
                                    byte[] msg = byteBufferTemp.array();
                                    size_mensagem += msg.length;
                                    nodo_mensagens.add(msg);
                                }

                            }

                            ByteBuffer byteBuffer = ByteBuffer.allocate(2+4+hashes.size()*20+size_mensagem);

                            byteBuffer.put(Serializer.intToTwoBytes(nodos_totais));
                            byteBuffer.put(Serializer.intToFourBytes(blocos_quantidade));
                            for (byte[] bytes : hashes){
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
                            in.readFully(fileName_bytes,0,size_fileName);
                            String fileName = new String(fileName_bytes,StandardCharsets.UTF_8);
                            byte[] blocoBytes = new byte[4];
                            in.readFully(blocoBytes, 0, 4);
                            int bloco = Serializer.fourBytesToInt(blocoBytes);
                            out.write(file_hash.get(fileName).get(bloco));
                            out.flush();
                            break;
                        }

                        case 0: {
                            System.out.println("Conexão fechada com nodo: " + nodeSocket.getInetAddress().getHostAddress());
                            loop = false;
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
