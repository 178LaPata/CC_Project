import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FS_Tracker {

    public static void main(String[] args) {

        ServerSocket tracker = null;

        try {

            tracker = new ServerSocket(9090);

            System.out.println("Servidor ativo em : " + InetAddress.getLocalHost().getHostAddress() + " porta " + tracker.getLocalPort());


            ConcurrentHashMap<String,List<FileInfo>> node_files = new ConcurrentHashMap<>();


            while (true) {

                Socket node = tracker.accept();

                System.out.println("Novo nodo conectado: " + node.getInetAddress().getHostAddress());

                NodeHandler nodeHandler = new NodeHandler(node,node_files);

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

        public NodeHandler(Socket socket, ConcurrentHashMap<String,List<FileInfo>> node_files)
        {
            this.nodeSocket = socket;
            this.node_files = node_files;
        }


        public void run()
        {

            try {

                String ip_node = nodeSocket.getInetAddress().getHostAddress();

                ObjectOutputStream out = new ObjectOutputStream(nodeSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(nodeSocket.getInputStream());

                boolean loop = true;

                while (loop) {

                    String received = in.readUTF();

                    switch (received){

                        case "UPDATE": {

                            int size = in.readInt();

                            List<FileInfo> files = new ArrayList<>();

                            for (int i = 0; i<size; i++){
                                FileInfo file = (FileInfo) in.readObject();
                                files.add(file);
                            }

                            node_files.put(ip_node,files);

                            System.out.println(node_files);

                            break;
                        }




                        case "GET":{

                            String file = in.readUTF();

                            for (Map.Entry<String,List<FileInfo>> entry : node_files.entrySet()){
                                List<FileInfo> fds = entry.getValue();
                                for (FileInfo name : entry.getValue()){
                                    for (String nome : )
                                }

                            }


                        }


                        case "QUIT":{
                            loop = false;
                            break;
                        }


                    }
                }

            }

            catch (IOException e) {
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

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
