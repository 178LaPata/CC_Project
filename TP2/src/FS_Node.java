import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
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


            if (register_message != null) {
                out.write(register_message);
                out.flush();
            }


            List<BlockToSend> blocksToSend = Collections.synchronizedList(new ArrayList<>());
            ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive = new ConcurrentHashMap<>();


            DatagramSocket socketUDP = new DatagramSocket(9090);

            UDPListener udpListener = new UDPListener(socketUDP, folder, blocksToSend, blocksToReceive, out);
            new Thread(udpListener).start();


            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));


            boolean loop = true;

            while (loop) {

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]) {

                    case "GET": {

                        //Verificar se nodo já tem o ficheiro
                        if (listOfFiles != null) {
                            boolean exists = false;
                            for (File f : listOfFiles) {
                                if (f.getName().equals(option[1])) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (exists)
                                break;
                        }


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


                        for (int i = 0; i < blocosTotais; i++) {
                            byte[] hash = new byte[20];
                            in.readFully(hash, 0, 20);
                            blocksToReceive.put(new BlockToReceive(option[1], i), hash);
                            System.out.println(i + Arrays.toString(hash));
                        }

                        //The rest of the message is the list of nodes
                        //Each node is represented by 4 bytes for the IP and 4 bytes for the number of blocks available
                        //If the number of blocks available is 0, the node has all the blocks
                        //If the number of blocks available is not 0, the next 4 bytes represent the block ID
                        //The number of blocks available is always followed by the block IDs

                        //I want to ask each node for the blocks it has
                        //I should ask for the blocks which are less common amongst the nodes first
                        //I should ask the nodes which are faster first
                        //I should ask the nodes which are more reliable first


                        //Concurrent Set of IPS
                        ConcurrentSkipListSet<String> ipsNodes = new ConcurrentSkipListSet<>();

                        ConcurrentHashMap<Integer, List<String>> nodesForBlocks = new ConcurrentHashMap<>();

                        for (int i = 0; i < blocosTotais; i++) {
                            nodesForBlocks.put(i, new ArrayList<>());
                        }

                        for (int i = 0; i < nodosTotais; i++) {

                            byte[] ipBytes = new byte[4];
                            in.readFully(ipBytes, 0, 4);
                            String ip = InetAddress.getByAddress(ipBytes).getHostAddress();


                            byte[] qtBlocosDisponiveisBytes = new byte[4];
                            in.readFully(qtBlocosDisponiveisBytes, 0, 4);
                            int qtBlocosDisponiveis = Serializer.fourBytesToInt(qtBlocosDisponiveisBytes);


                            ipsNodes.add(ip);
                            if (qtBlocosDisponiveis == 0) {
                                for (int b = 0; b < blocosTotais; b++) {
                                    //blockAvailability.get(b).add(ip);
                                    nodesForBlocks.get(b).add(ip);
                                }
                            } else {
                                for (int b = 0; b < qtBlocosDisponiveis; b++) {
                                    byte[] blocoIDBytes = new byte[4];
                                    in.readFully(blocoIDBytes, 0, 4);
                                    int bloco = Serializer.fourBytesToInt(blocoIDBytes);
                                    //blockAvailability.get(bloco).add(ip);
                                    nodesForBlocks.get(bloco).add(ip);
                                }
                            }
                        }


                        //Concurrent Set of BlockPriority
                        ConcurrentSkipListSet<BlockPriority> blockPrioritySet = new ConcurrentSkipListSet<>(Comparator.comparingInt(bp -> nodesForBlocks.get(bp.id).size()));
                        blockPrioritySet.addAll(nodesForBlocks.keySet().stream().map(BlockPriority::new).collect(Collectors.toList()));

                        ConcurrentSkipListSet<String> ipsToTest = new ConcurrentSkipListSet<>();

                        //Transfer blocks by order of priority

                        //open file with name option[1] and create it if it doesnt exist
                        File file = new File(folder, option[1]);
                        if (!file.exists()) {
                            file.createNewFile();
                        }



                        /*

                        //Thread executor = Executors.newFixedThreadPool(10);
                        ExecutorService executor = Executors.newFixedThreadPool(nodosTotais);




                        //run UDPTransferThread for each block until all blocks are transferred
                        for (int i = 0; i < nodosTotais; i++) {
                            executor.execute(new UDPRequestBlock(socketUDP, option[1], blockPrioritySet, ipsNodes, nodesForBlocks, blocksToReceive,ipsToTest));
                        }

                        //wait for all threads to finish
                        executor.shutdown();

                         */


                        //CheckNode checkNode = new CheckNode(ipsNodes, ipsToTest, out, in);
                        //new Thread(checkNode).start();

                        UDPRequestBlock udpRequestBlock = new UDPRequestBlock(socketUDP, option[1], blockPrioritySet, ipsNodes, nodesForBlocks, blocksToReceive,ipsToTest);
                        Thread[] threads = new Thread[nodosTotais];

                        for (int i = 0; i <nodosTotais; i++){
                            threads[i] = new Thread(udpRequestBlock);
                            threads[i].start();
                        }

                        for (int i = 0; i<nodosTotais; i++){
                            threads[i].join();
                        }

                        System.out.println("Transferência concluída");

                        //checkNode.interrupt();

                        /*
                        Map<Integer, Set<String>> blockAvailability = new HashMap<>();
                        Set<String> ipsNodes = new HashSet<>();



                        for (int i = 0; i < blocosTotais; i++) {
                            blockAvailability.put(i, new HashSet<>());
                        }


                        ConcurrentHashMap<Integer, List<String>> nodesForBlocks = new ConcurrentHashMap<>();
                        for (int i = 0; i < blocosTotais; i++) {
                            nodesForBlocks.put(i, new ArrayList<>());
                        }

                        for (int i = 0; i < nodosTotais; i++) {

                            byte[] ipBytes = new byte[4];
                            in.readFully(ipBytes, 0, 4);
                            String ip = InetAddress.getByAddress(ipBytes).getHostAddress();


                            byte[] qtBlocosDisponiveisBytes = new byte[4];
                            in.readFully(qtBlocosDisponiveisBytes, 0, 4);
                            int qtBlocosDisponiveis = Serializer.fourBytesToInt(qtBlocosDisponiveisBytes);


                            //ipsNodes.add(ip);
                            if (qtBlocosDisponiveis == 0) {
                                for (int b = 0; b < blocosTotais; b++) {
                                    //blockAvailability.get(b).add(ip);
                                    nodesForBlocks.get(b).add(ip);
                                }
                            } else {
                                for (int b = 0; b < qtBlocosDisponiveis; b++) {
                                    byte[] blocoIDBytes = new byte[4];
                                    in.readFully(blocoIDBytes, 0, 4);
                                    int bloco = Serializer.fourBytesToInt(blocoIDBytes);
                                    //blockAvailability.get(bloco).add(ip);
                                    nodesForBlocks.get(bloco).add(ip);
                                }
                            }
                        }

                        /*
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



                        File foldertst = new File("node2/");
                        File file = new File(foldertst, option[1]);

                        // UDPTransferThread udpTransferThread = new UDPTransferThread(5001, blockAvailability, blockPrioritySet, nodePrioritySet, file, out, in);

                        UDPTransferThread udpTransferThread = new UDPTransferThread(5001, file, nodesForBlocks);

                        Thread thread = new Thread(udpTransferThread);

                        thread.start();
                        thread.join();

                        /*
                        for (int i = 0; i < nodosTotais; i++) {
                            new Thread(udpTransferThread).start();
                        }

                        for (int i = 0; i < nodosTotais; i++) {
                            new Thread(udpTransferThread).join();
                        }

                         */


                        break;
                    }


                    case "QUIT": {
                        out.writeByte(0);
                        out.flush();
                        udpListener.stopThread();
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


    private static class UDPRequestBlock extends Thread {

        private DatagramSocket socket;
        private String fileName;
        private ConcurrentSkipListSet<BlockPriority> blockPrioritySet;
        private ConcurrentSkipListSet<String> ipsNodes;
        ConcurrentHashMap<Integer, List<String>> nodesForBlocks;
        ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        ConcurrentSkipListSet<String> ipsToTest;


        public UDPRequestBlock(DatagramSocket socket, String fileName, ConcurrentSkipListSet<BlockPriority> blockPrioritySet,
                               ConcurrentSkipListSet<String> ipsNodes, ConcurrentHashMap<Integer, List<String>> nodesForBlocks,
                               ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, ConcurrentSkipListSet<String> ipsToTest) {
            try {
                this.socket = socket;
                this.fileName = fileName;
                this.blockPrioritySet = blockPrioritySet;
                this.ipsNodes = ipsNodes;
                this.nodesForBlocks = nodesForBlocks;
                this.blocksToReceive = blocksToReceive;
                this.ipsToTest = ipsToTest;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() {

            while (!blockPrioritySet.isEmpty()) {

                for (BlockPriority blockPriority : blockPrioritySet) {

                    if (blockPriority.available) {

                        blockPriority.available = false;

                        //Create datagram to request for block
                        byte[] name = fileName.getBytes();
                        byte size_name = (byte) name.length;
                        byte[] blockID = Serializer.intToFourBytes(blockPriority.id);
                        ByteBuffer msg_buffer = ByteBuffer.allocate(2 + name.length + 4);
                        msg_buffer.put((byte) 0);
                        msg_buffer.put(size_name);
                        msg_buffer.put(name);
                        msg_buffer.put(blockID);
                        byte[] msg = msg_buffer.array();


                        List<String> ips = nodesForBlocks.get(blockPriority.id);

                        for (String ip : ips) {

                            if (ips.contains(ip)) {

                                ipsNodes.remove(ip);

                                BlockToReceive blockToReceive = new BlockToReceive(fileName, blockPriority.id);

                                System.out.println(Arrays.toString(msg));

                                try {

                                    DatagramPacket requestPacket = new DatagramPacket(msg, msg.length, InetAddress.getByName(ip), 9090);

                                    int attempt;
                                    for (attempt = 0; attempt < 3; attempt++) {
                                        socket.send(requestPacket);
                                        Thread.sleep(1000);
                                        if (!blocksToReceive.containsKey(blockToReceive)) {
                                            blockPrioritySet.remove(blockPriority);
                                            break;
                                        }
                                    }
                                    if (attempt == 3) {
                                        blockPriority.available = true;
                                        ipsToTest.add(ip);
                                        //ask transfer if this ip is still available
                                        //if not, remove from ipsNodes
                                        //if yes, try again
                                    }
                                    else ipsNodes.add(ip);

                                } catch (IOException | InterruptedException ex) {
                                    throw new RuntimeException(ex);
                                }

                                if (blocksToReceive.get(blockToReceive) == null)
                                    break;
                            }
                        }

                    }

                }


            }


        }

    }

    //Create thread that will do something everytime ipsToTest is updated
    //If ip is not available, remove from ipsNodes
    public static class CheckNode extends Thread {

            ConcurrentSkipListSet<String> ipsNodes;
            ConcurrentSkipListSet<String> ipsToTest;
            DataOutputStream out;
            DataInputStream in;


            public CheckNode(ConcurrentSkipListSet<String> ipsNodes, ConcurrentSkipListSet<String> ipsToTest, DataOutputStream out, DataInputStream in){
                this.ipsNodes = ipsNodes;
                this.ipsToTest = ipsToTest;
                this.out = out;
                this.in = in;
            }

            @Override
            public void run() {

                while (true) {

                    for (String ip : ipsToTest) {

                        //send message thru out to tracker asking if ip is still available
                        try {
                            out.write(TPManager.checkNodeMessage(ip));
                            out.flush();
                            byte response = in.readByte();
                            if (response == 0)
                                ipsNodes.add(ip);
                            ipsToTest.remove(ip);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                }

            }

    }


    public static class UDPListener extends Thread {

        private DatagramSocket socket;
        private byte[] buffer;
        private File folder;
        List<BlockToSend> blocksToSend;
        ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        DataOutputStream out;

        public UDPListener(DatagramSocket socket, File folder, List<BlockToSend> blocksToSend, ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, DataOutputStream out) {
            try {
                // Create a DatagramSocket to listen on the specified port
                this.socket = socket;
                // Set a buffer for receiving data
                this.buffer = new byte[1024];
                this.folder = folder;
                this.blocksToSend = blocksToSend;
                this.blocksToReceive = blocksToReceive;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stopThread() {
            socket.close();
            this.interrupt();
        }

        @Override
        public void run() {

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (true) {
                try {

                    // Receive data from the socket
                    socket.receive(packet);

                    new Thread(new UDPDataHandler(socket, packet, folder, blocksToSend, blocksToReceive, out)).start();

                }
                catch (IOException e) {
                    // If the socket was closed, just terminate the thread
                    if (socket.isClosed()) {
                        return;
                    }
                    // Otherwise print the error
                    e.printStackTrace();
                }
            }
        }

    }

    //Create UPDRequestHandler class
    public static class UDPDataHandler implements Runnable {

        private DatagramSocket socket;
        private DatagramPacket packet;
        private File folder;

        List<BlockToSend> blocksToSend;
        ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive;
        DataOutputStream out;


        public UDPDataHandler(DatagramSocket socket, DatagramPacket packet, File folder, List<BlockToSend> blocksToSend, ConcurrentHashMap<BlockToReceive, byte[]> blocksToReceive, DataOutputStream out) {
            this.socket = socket;
            this.packet = packet;
            this.folder = folder;
            this.blocksToSend = blocksToSend;
            this.blocksToReceive = blocksToReceive;
            this.out = out;
        }

        @Override
        public void run() {

            try {

                byte[] receivedData = packet.getData();

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();


                int size_name = receivedData[1];
                String name = new String(receivedData, 2, size_name);
                int blockID = Serializer.fourBytesToInt(Arrays.copyOfRange(receivedData, 2 + size_name, 2 + size_name + 4));

                if (folder.listFiles()==null)
                    return;

                File file = null;
                for (File f : folder.listFiles()) {
                    // Check if the current file is the right name
                    if (f.getName().equals(name)) {
                        file = f;
                        break;
                    }
                }

                if (file == null)
                    return;


                if (receivedData[0] == 0) {

                    try {

                        BlockToSend blockToSend = new BlockToSend(name, blockID, senderAddress.getHostAddress());

                        if (!blocksToSend.contains(blockToSend))
                            blocksToSend.add(blockToSend);

                        // Open the file using RandomAccessFile
                        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                            // Set the file pointer to the desired offset
                            randomAccessFile.seek(blockID * 500);
                            // Read 500 bytes from the current offset
                            byte[] dataBuffer = new byte[500];
                            int bytesread = randomAccessFile.read(dataBuffer);

                            byte[] data = new byte[bytesread];
                            System.arraycopy(dataBuffer, 0, data, 0, bytesread);

                            ByteBuffer msg_buffer = ByteBuffer.allocate(6 + size_name + data.length);
                            msg_buffer.put((byte) 1);
                            msg_buffer.put((byte) size_name);
                            msg_buffer.put(name.getBytes());
                            msg_buffer.put(Serializer.intToFourBytes(blockID));
                            msg_buffer.put(data);
                            byte[] msg = msg_buffer.array();

                            DatagramPacket responsePacket = new DatagramPacket(msg, msg.length, senderAddress, senderPort);


                            for (int attempt = 0; attempt < 3; attempt++) {
                                socket.send(responsePacket);
                                Thread.sleep(1000);
                                if (!blocksToSend.contains(blockToSend))
                                    break;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (RuntimeException e) {
                        throw new RuntimeException(e);
                    }


                } else if (receivedData[0] == 1) {

                    BlockToReceive blockToReceive = new BlockToReceive(name, blockID);

                    byte[] nameFileBytes = file.getName().getBytes();
                    byte nameFileBytesSize = (byte) nameFileBytes.length;
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1 + nameFileBytesSize + 4);
                    byteBuffer.put((byte) 2);
                    byteBuffer.put(nameFileBytesSize);
                    byteBuffer.put(nameFileBytes);
                    byteBuffer.put(Serializer.intToFourBytes(blockID));

                    //Send datagram
                    DatagramPacket ackPacket = new DatagramPacket(byteBuffer.array(), byteBuffer.array().length, senderAddress, senderPort);

                    if (!blocksToReceive.contains(blockToReceive)) {
                        socket.send(ackPacket);
                        return;
                    }

                    int receivedLength = packet.getLength(); // Actual length of received data

                    // Ignore empty or unused part of the packet
                    byte[] actualData = new byte[receivedLength - 6 - size_name];

                    // Copy the data from the packet to the actualData array
                    System.arraycopy(packet.getData(), 6 - 1 + size_name, actualData, 0, receivedLength - 6 + 1 - size_name);


                    /*
                    MessageDigest digest = MessageDigest.getInstance("SHA-1");
                    digest.update(actualData, 0, actualData.length);




                    byte[] dataHashBytes = digest.digest();


                    if (blocksToReceive.get(blockToReceive) == dataHashBytes) {
                        blocksToReceive.remove(blockToReceive);
                        //Write data in file using RandomAccessFile
                        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                            // Set the file pointer to the desired offset
                            randomAccessFile.seek(blockID * 500);
                            // Write 500 bytes from the current offset
                            randomAccessFile.write(actualData);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        //Send tcp message to tracker to update file info
                        out.write(TPManager.updateMessage(file.getName(), blockID));
                        out.flush();


                        //Create a DatagramPacket to send data to ACK that the data was received
                        //Put the name of the file on the datagram

                        socket.send(ackPacket);
                    }

                     */

                    System.out.println("DATA QUE VAI SER ESCRITA" + Arrays.toString(actualData));

                    try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                        // Set the file pointer to the desired offset
                        randomAccessFile.seek(blockID * 500);
                        // Write 500 bytes from the current offset
                        randomAccessFile.write(actualData);
                        blocksToReceive.remove(blockToReceive);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    //Send tcp message to tracker to update file info
                    out.write(TPManager.updateMessage(file.getName(), blockID));
                    out.flush();


                    //Create a DatagramPacket to send data to ACK that the data was received
                    //Put the name of the file on the datagram
                    socket.send(ackPacket);


                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static class BlockToSend {

        String nameFile;
        int id;
        String ip;

        public BlockToSend(String nameFile, int id, String ip) {
            this.nameFile = nameFile;
            this.id = id;
            this.ip = ip;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BlockToSend other = (BlockToSend) obj;
            return id == other.id &&
                    Objects.equals(nameFile, other.nameFile) &&
                    Objects.equals(ip, other.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nameFile, id, ip);
        }

    }

    public static class BlockToReceive {

        String nameFile;
        int id;

        public BlockToReceive(String nameFile, int id) {
            this.nameFile = nameFile;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BlockToReceive other = (BlockToReceive) obj;
            return id == other.id && Objects.equals(nameFile, other.nameFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
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

    /*
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

     */


}
