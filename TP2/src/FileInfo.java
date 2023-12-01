import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FileInfo implements Comparable<FileInfo> {

    private String nome;
    private int blocos_quantidade;
    List<Integer> blocos_disponiveis;
    boolean complete = false;

    public FileInfo(String nome, int blocos_quantidade) {
        this.nome = nome;
        this.blocos_quantidade = blocos_quantidade;
        complete = true;
    }

    public FileInfo(String nome, int blocos_quantidade, List<Integer> blocos_disponiveis) {
        this.nome = nome;
        this.blocos_quantidade = blocos_quantidade;
        if (blocos_quantidade == blocos_disponiveis.size())
            complete = true;
        else
            this.blocos_disponiveis = blocos_disponiveis;
    }


    @Override
    public String toString() {
        if (blocos_disponiveis == null)
            return this.nome + ";" + this.blocos_quantidade;
        else
            return this.nome + ";" + this.blocos_quantidade + ";" + this.blocos_disponiveis;
    }

    public String getNome() {
        return nome;
    }

    public int getBlocos_quantidade() {
        return blocos_quantidade;
    }


    @Override
    public int compareTo(FileInfo other) {
        return this.nome.compareTo(other.nome);
    }


    public byte[] fileInfoToBytes() {

        byte size_name = (byte) this.nome.length();
        byte[] name = this.nome.getBytes();
        byte[] blocos_quantidade = Serializer.intToFourBytes(this.blocos_quantidade);
        byte[] blocos_disponiveis_size;
        byte[] blocos_disponiveis;

        if (this.complete) {
            blocos_disponiveis_size = Serializer.intToFourBytes(0);
            blocos_disponiveis = new byte[0];
        }


        else {

            blocos_disponiveis_size = Serializer.intToFourBytes(this.blocos_disponiveis.size());

            ByteBuffer buffer_blocos_disponiveis = ByteBuffer.allocate(this.blocos_disponiveis.size() * 4);

            for (Integer i : this.blocos_disponiveis)
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


}