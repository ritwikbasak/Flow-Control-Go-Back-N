
//@author Ritwik Basak
import java.net.*;
import java.io.*;
import java.util.*;

public class MServer extends Thread {

	private static final int TIMEOUT = 30;
	private static final double LOSS_RATE = 0.3;

	private final DatagramSocket serverSocket;
	private final InetAddress clientIp;
	private final int clientPort;
	private final int windowSize;

	private final String request;
	private FileInputStream fileIn;
	private int nextPacket;

	private boolean noConsignment;

	public MServer(DatagramSocket serverSocket, DatagramPacket requestPacket, int N) throws IOException {
		this.serverSocket = serverSocket;
		this.clientIp = requestPacket.getAddress();
		this.clientPort = requestPacket.getPort();

		this.windowSize = N;

		request = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(requestPacket.getData())))
				.readLine();

		fileIn = null;
		nextPacket = 1;
		noConsignment = false;

	}

	private int increment(int num) {
		return (num) % 127 + 1;
	}

	private byte[] getNextConsignment() throws IOException {

		if (noConsignment)
			return null;

		byte[] begin = new byte[6];
		byte[] begin0 = "RDT ".getBytes();
		byte[] begin1 = " ".getBytes();
		System.arraycopy(begin0, 0, begin, 0, 4);
		begin[4] = (byte) nextPacket;
		System.arraycopy(begin1, 0, begin, 5, 1);
		// ("RDT " + byte(currentPacket) + " ").getBytes();

		byte[] payload = new byte[512];

		int result = fileIn.read(payload);
		result = (result == -1) ? 0 : result;

		String lastString = " \r\n";

		if (result < 512) {

			lastString = " END" + lastString;
			noConsignment = true;

		}

		byte[] lastBytes = lastString.getBytes();

		byte[] sentData = new byte[begin.length + result + lastBytes.length];
		System.arraycopy(begin, 0, sentData, 0, begin.length);

		if (result != 0)
			System.arraycopy(payload, 0, sentData, begin.length, result);

		System.arraycopy(lastBytes, 0, sentData, begin.length + result, lastBytes.length);

		nextPacket = increment(nextPacket);
		return sentData;

	}

	private void fireAll(LinkedList<byte[]> window, String fileName) throws IOException {

		Random random = new Random();

		for (int i = 0; i < window.size(); i++) {

			byte[] current = window.get(i);
			DatagramPacket sentPacket = new DatagramPacket(current, current.length, clientIp, clientPort);

			// drop packet probabilistically
			if (random.nextDouble() >= LOSS_RATE) {

				System.out.println();
				System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

				System.out.println();
				System.out.println("Sent CONSIGNMENT " + (int) (current[4]));

				serverSocket.send(sentPacket);

				System.out.println();
				System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

				System.out.println();
				System.out.println(new String(current));

			} else {

				System.out.println();
				System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

				System.out.println();
				System.out.println("Forgot CONSIGNMENT " + (int) current[4]);

			}

		}

	}

	private void moveWindow(LinkedList<byte[]> window, int places) throws IOException {

		for (int i = 1; i <= places && !window.isEmpty(); i++) {

			window.remove();

		}

		byte[] temp;
		while (window.size() < windowSize && (temp = getNextConsignment()) != null)
			window.add(temp);

	}

	public void run() {

		try {

			if (!request.startsWith("REQUEST")) {

				System.out.println();
				System.out.println("CLIENT PORT = " + clientPort);

				System.out.println();
				System.out.println("Invalid Client Request");
				return;

			}

			String fileName = request.substring(request.indexOf("REQUEST") + "REQUEST".length(), request.length());

			System.out.println();
			System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

			System.out.println();
			System.out.println("Received request for " + fileName + " from " + clientIp + " port " + clientPort);

			// read file into buffer
			fileIn = new FileInputStream(fileName);

			serverSocket.setSoTimeout(TIMEOUT);

			LinkedList<byte[]> window = new LinkedList<>();
			moveWindow(window, 0);

			while (!window.isEmpty()) {

				byte[] readBuffer = new byte[1000];
				DatagramPacket receivedPacket = new DatagramPacket(readBuffer, readBuffer.length);

				fireAll(window, fileName);

				int highestAck = window.peekFirst()[4];

				for (int i = 1; i <= window.size(); i++) {

					try {

						serverSocket.receive(receivedPacket);
						int ack = readBuffer[4];

						// The consignment numbers and acknowledgements are in the range 1 .. 127
						// The acknowledgement 0 means END OF TRANSMISSION
						if (ack == 0)
							ack = increment(window.peekLast()[4]);

						// Updating highestAck
						for (int j = highestAck; j != increment(window.peekLast()[4]); j = increment(j))
							if (j == ack)
								highestAck = ack;

					} catch (SocketTimeoutException e) {

					}
				}

				if (highestAck == window.peekFirst()[4]) {

					System.out.println();
					System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

					System.out.println();
					System.out.println("Timeout");

				} else {

					System.out.println();
					System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

					System.out.println();
					System.out.print("Received ACK for packets : ");

					for (int i = window.peekFirst()[4]; i != highestAck; i = increment(i))
						System.out.print(i + "  ");

					System.out.println();

				}

				int places = 0;
				for (int i = window.peekFirst()[4]; i != highestAck; i = increment(i))
					places++;

				moveWindow(window, places);

				receivedPacket = null;

			}

			System.out.println();
			System.out.println("CLIENT PORT = " + clientPort + "\nFile = " + fileName);

			System.out.println();
			System.out.println("END");

		} catch (IOException e) {
			System.out.println(e);
			System.out.println(e.getStackTrace()[0].getLineNumber());

		} finally {
			try {
				if (fileIn != null)
					fileIn.close();
			} catch (IOException e) {
				System.out.println(e);
				System.out.println(e.getStackTrace()[0].getLineNumber());
			}
		}

	}

}
