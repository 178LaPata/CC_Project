import java.nio.ByteBuffer;
import java.util.*;

public class FileInfo implements Comparable<FileInfo> {

    private final String nome;
    private final int blockAmount;
    private Set<Integer> blocksAvailable;
    private boolean complete = false;

    public FileInfo(String nome, int blockAmount) {
        this.nome = nome;
        this.blockAmount = blockAmount;
        complete = true;
    }

    public FileInfo(String nome, int blockAmount, Set<Integer> blocksAvailable) {
        this.nome = nome;
        this.blockAmount = blockAmount;
        if (blockAmount == blocksAvailable.size())
            complete = true;
        else
            this.blocksAvailable = blocksAvailable;
    }

    public String getNome() {
        return nome;
    }

    public int getBlockAmount() {
        return blockAmount;
    }

    public boolean isComplete() {
        return complete;
    }

    public Set<Integer> getBlocksAvailable() {
        return new HashSet<>(blocksAvailable);
    }


    @Override
    public String toString() {
        if (blocksAvailable == null)
            return this.nome + ";" + this.blockAmount;
        else
            return this.nome + ";" + this.blockAmount + ";" + this.blocksAvailable;
    }


    @Override
    public int compareTo(FileInfo other) {
        return this.nome.compareTo(other.nome);
    }







    public byte[] fileInfoToBytes() {

        byte size_name = (byte) this.nome.length();
        byte[] name = this.nome.getBytes();
        byte[] blocos_quantidade = Serializer.intToFourBytes(this.blockAmount);
        byte[] blocos_disponiveis_size;
        byte[] blocos_disponiveis;

        if (this.complete) {
            blocos_disponiveis_size = Serializer.intToFourBytes(0);
            blocos_disponiveis = new byte[0];
        }


        else {

            blocos_disponiveis_size = Serializer.intToFourBytes(this.blocksAvailable.size());

            ByteBuffer buffer_blocos_disponiveis = ByteBuffer.allocate(this.blocksAvailable.size() * 4);

            for (Integer i : this.blocksAvailable)
                buffer_blocos_disponiveis.put(Serializer.intToFourBytes(i));

            blocos_disponiveis = buffer_blocos_disponiveis.array();
        }

        ByteBuffer buffer = ByteBuffer.allocate(1 + name.length + 4 + 4 + blocos_disponiveis.length);

        buffer.put(size_name);
        buffer.put(name);
        buffer.put(blocos_quantidade);
        buffer.put(blocos_disponiveis_size);
        buffer.put(blocos_disponiveis);

        return buffer.array();
    }

    public void addBlocoDisponivel(int bloco){
        blocksAvailable.add(bloco);
        if (blocksAvailable.size() == blockAmount) {
            complete = true;
            blocksAvailable = null;
        }
    }


}