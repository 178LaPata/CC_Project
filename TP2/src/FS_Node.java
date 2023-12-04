import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLParagraphElement;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

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


            byte[] register_message = TPManager.registerMessage(listOfFiles);

            //System.out.println(new String(Requests.create_request(fds),StandardCharsets.UTF_8));

            if (register_message != null) {
                out.write(register_message);
                out.flush();
            }


            UDPGetRequestsThread UDPGetRequestsThread = new UDPGetRequestsThread(9090, listOfFiles);
            UDPGetRequestsThread.start();

            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));


            boolean loop = true;

            while (loop) {

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]) {

                    case "GET": {
                        out.write(TPManager.getFileMessage(option[1]));
                        out.flush();

                        byte[] nodosTotaisBytes = new byte[2];
                        in.readFully(nodosTotaisBytes, 0, 2);
                        int nodosTotais = Serializer.twoBytesToInt(nodosTotaisBytes);

                        if (nodosTotais == 0) {
                            System.out.println("Ficheiro não existe!");
                            break;
                        }


                        byte[] blocosTotaisBytes = new byte[4];
                        in.readFully(blocosTotaisBytes, 0, 4);
                        int blocosTotais = Serializer.fourBytesToInt(blocosTotaisBytes);


                        Map<Integer, Set<String>> blockAvailability = new HashMap<>();
                        Set<String> ipsNodes = new HashSet<>();

                        for (int i = 0; i < blocosTotais; i++) {
                            blockAvailability.put(i, new HashSet<>());
                        }


                        for (int i = 0; i < nodosTotais; i++) {

                            byte[] ipBytes = new byte[4];
                            in.readFully(ipBytes, 0, 4);
                            String ip = InetAddress.getByAddress(ipBytes).getHostAddress();

                            System.out.println(ip);

                            byte[] qtBlocosDisponiveisBytes = new byte[4];
                            in.readFully(qtBlocosDisponiveisBytes, 0, 4);
                            int qtBlocosDisponiveis = Serializer.fourBytesToInt(qtBlocosDisponiveisBytes);

                            ipsNodes.add(ip);
                            if (qtBlocosDisponiveis == 0) {
                                for (int b = 0; b < blocosTotais; b++) {
                                    blockAvailability.get(b).add(ip);
                                }
                            } else {
                                for (int b = 0; b < qtBlocosDisponiveis; b++) {
                                    byte[] blocoIDBytes = new byte[4];
                                    in.readFully(blocoIDBytes, 0, 4);
                                    int bloco = Serializer.fourBytesToInt(blocoIDBytes);
                                    blockAvailability.get(bloco).add(ip);
                                }
                            }
                        }

                        List<BlockPriority> blockPriorityList = blockAvailability
                                .keySet()
                                .stream()
                                .map(BlockPriority::new)
                                .collect(Collectors.toList());

                        ConcurrentSkipListSet<BlockPriority> blockPrioritySet =
                                new ConcurrentSkipListSet<>(Comparator.comparingInt(bp -> blockAvailability.get(bp.id).size()));
                        blockPrioritySet.addAll(blockPriorityList);
                        ConcurrentSkipListSet<NodePriority> nodePrioritySet = new ConcurrentSkipListSet<>(Comparator.comparingInt(a -> a.ping));


                        for (String ip : ipsNodes) {

                            try {

                                InetAddress inetAddress = InetAddress.getByName(ip);
                                long totalTime;

                                // Perform the ping by sending an ICMP Echo Request
                                long startTime = System.currentTimeMillis();
                                if (inetAddress.isReachable(5000)) { // 5000 milliseconds timeout
                                    long endTime = System.currentTimeMillis();
                                    totalTime = endTime - startTime;
                                    nodePrioritySet.add(new NodePriority(ip, (int) totalTime));
                                } else {
                                    nodePrioritySet.add(new NodePriority(ip, 5001));
                                }
                            } catch (Exception e) {
                                System.err.println(e.getMessage());
                            }
                        }

                        File file = new File(folder, option[1]);

                        UDPTransferThread udpTransferThread = new UDPTransferThread(5001, blockAvailability, blockPrioritySet, nodePrioritySet, file);

                        for (int i = 0; i < nodosTotais; i++) {
                            new Thread(udpTransferThread).start();
                        }

                        for (int i = 0; i < nodosTotais; i++) {
                            new Thread(udpTransferThread).join();
                        }


                        break;
                    }

                    case "QUIT": {
                        out.writeByte(0);
                        out.flush();
                        loop = false;
                        break;
                    }

                    case "FILES": {
                        out.writeByte(3);
                        out.flush();
                        byte[] numberFiles_bytes = new byte[2];
                        in.readFully(numberFiles_bytes, 0, 2);
                        int numberFiles = Serializer.twoBytesToInt(numberFiles_bytes);
                        if (numberFiles == 0) {
                            System.out.println("Não existem ficheiros disponíveis para transferência");
                            break;
                        }
                        List<String> namesList = new ArrayList<>();
                        for (int i = 0; i < numberFiles; i++) {
                            int nameSize = in.readByte();
                            byte[] name_byte = new byte[nameSize];
                            in.readFully(name_byte, 0, nameSize);
                            String name = new String(name_byte, StandardCharsets.UTF_8);
                            namesList.add(name);
                        }
                        System.out.println(namesList);
                        break;
                    }

                    /*
                    case "GET":{


                    }


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

                     */

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


        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


    private static class UDPGetRequestsThread extends Thread {

        private DatagramSocket socket;
        private byte[] buffer;
        private File[] listOfFiles;

        public UDPGetRequestsThread(int port, File[] listOfFiles) {
            try {
                // Create a DatagramSocket to listen on the specified port
                this.socket = new DatagramSocket(port);
                // Set a buffer for receiving data
                this.buffer = new byte[1024];
                this.listOfFiles = listOfFiles;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Create a DatagramPacket to receive data
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // Receive data from the socket
                    socket.receive(packet);

                    byte[] receivedData = packet.getData();

                    InetAddress senderAddress = packet.getAddress();
                    int senderPort = packet.getPort();


                    int size_name = receivedData[0];
                    String name = new String(receivedData, 1, size_name);
                    int blockID = Serializer.fourBytesToInt(Arrays.copyOfRange(receivedData, 1 + size_name, 1 + size_name + 4));

                    try {
                        // Iterate through the list of files
                        for (File file : listOfFiles) {
                            // Check if the current file is the right name
                            if (file.getName().equals(name)) {
                                // Open the file using RandomAccessFile
                                try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                                    // Set the file pointer to the desired offset
                                    randomAccessFile.seek(blockID * 500);
                                    // Read 500 bytes from the current offset
                                    byte[] data = new byte[500];
                                    randomAccessFile.read(data);

                                    ByteBuffer msg_buffer = ByteBuffer.allocate(4 + data.length);
                                    msg_buffer.put(Serializer.intToFourBytes(blockID));
                                    msg_buffer.put(data);
                                    byte[] msg = msg_buffer.array();

                                    DatagramPacket responsePacket = new DatagramPacket(msg, msg.length, senderAddress, senderPort);
                                    socket.send(responsePacket);
                                    break;
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        break;
                    } catch (RuntimeException e) {
                        throw new RuntimeException(e);
                    }

                }


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close the socket when the thread is interrupted or finished
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }

    }


    private static class UDPTransferThread extends Thread {

        private DatagramSocket socket;
        private byte[] buffer;
        private Map<Integer, Set<String>> blockAvailability;
        private ConcurrentSkipListSet<BlockPriority> blockPrioritySet;
        private ConcurrentSkipListSet<NodePriority> nodePrioritySet;
        private File file;

        public UDPTransferThread(int port, Map<Integer, Set<String>> blockAvailability, ConcurrentSkipListSet<BlockPriority> blockPrioritySet, ConcurrentSkipListSet<NodePriority> nodePrioritySet, File file) {
            try {
                // Create a DatagramSocket to listen on the specified port
                this.socket = new DatagramSocket(port);
                // Set a buffer for receiving data
                this.buffer = new byte[1024];
                this.blockAvailability = blockAvailability;
                this.blockPrioritySet = blockPrioritySet;
                this.nodePrioritySet = nodePrioritySet;
                this.file = file;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            try {


                while (!blockPrioritySet.isEmpty()) {


                    Iterator<BlockPriority> iteratorBlockPrioritySet = blockPrioritySet.iterator();

                    while (iteratorBlockPrioritySet.hasNext()) {

                        BlockPriority block = iteratorBlockPrioritySet.next();

                        if (block.available == true) {

                            Set<String> blockIps = blockAvailability.get(block.id);

                            Iterator<NodePriority> nodePriorityIterator = nodePrioritySet.iterator();

                            while (nodePriorityIterator.hasNext()) {

                                NodePriority nodePriority = nodePriorityIterator.next();

                                if (blockIps.contains(nodePriority.ip) && nodePriority.available) {

                                    block.available = false;
                                    nodePriority.available = false;

                                    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

                                    byte[] name = file.getName().getBytes();
                                    byte size_name = (byte) name.length;
                                    byte[] blockID = Serializer.intToFourBytes(block.id);
                                    ByteBuffer msg_buffer = ByteBuffer.allocate(1 + name.length + 4);
                                    msg_buffer.put(size_name);
                                    msg_buffer.put(name);
                                    msg_buffer.put(blockID);
                                    byte[] msg = msg_buffer.array();

                                    DatagramPacket requestPacket = new DatagramPacket(msg, msg.length, InetAddress.getByName(nodePriority.ip), 9090);
                                    DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);


                                    int attempt = 0;

                                    do {
                                        try {
                                            // Send the DatagramPacket
                                            socket.send(requestPacket);

                                            // Set a timeout on the socket for receiving the response
                                            socket.setSoTimeout(5000);

                                            // Attempt to receive a response within the specified timeout
                                            socket.receive(requestPacket);

                                            // Process the received data
                                            socket.receive(dataPacket);

                                            // Reset the socket timeout for future receives
                                            socket.setSoTimeout(0);

                                            // Exit the loop as we have received a response
                                            break;

                                        } catch (SocketTimeoutException e) {
                                            attempt++;
                                            if (attempt == 3)
                                                break;
                                        }
                                    } while (true);


                                }


                            }


                        }

                    }


                }


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Close the socket when the thread is interrupted or finished
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }

    }


    public static class BlockPriority {
        int id;
        boolean available;

        private BlockPriority(int id) {
            this.id = id;
            this.available = true;
        }
    }

    public static class NodePriority {
        String ip;
        int ping;
        boolean available;

        private NodePriority(String ip, int ping) {
            this.ip = ip;
            this.ping = ping;
            available = true;
        }
    }


}
