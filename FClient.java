
//@author Ravi Ranjan
import java.net.*;
import java.io.*;
import java.util.*;

public class FClient {

	private static final int AVERAGE_DELAY = 10;
	private static final double LOSS_RATE = 0.3;

	private static int increment(int num) {
		return (num) % 127 + 1;
	}

	private static byte[] getMessage(int ackNo) {

		byte begin[] = new byte[8];
		byte begin0[] = "ACK ".getBytes();
		byte begin1[] = " \r\n".getBytes();

		System.arraycopy(begin0, 0, begin, 0, 4);
		begin[4] = (byte) ackNo;
		System.arraycopy(begin1, 0, begin, 5, 3);

		return begin;
		// "ACK " + byte(expectedPacket) + " \r\n";
	}

	public static void main(String[] args) {

		DatagramSocket clientSocket = null;
		FileOutputStream fileOut = null;

		try {

			clientSocket = new DatagramSocket();

			byte[] readBuffer, sendBuffer;

			String fileName = args[2];
			fileOut = new FileOutputStream("output_" + clientSocket.getLocalPort() + "_" + fileName);

			sendBuffer = ("REQUEST" + fileName + "\r\n").getBytes();
			DatagramPacket sentPacket = new DatagramPacket(sendBuffer, sendBuffer.length,
					InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
			clientSocket.send(sentPacket);

			Random random = new Random();
			int expectedPacket = 1;
			boolean end = false;

			System.out.println();
			System.out.println("Requesting " + fileName + " from " + args[0] + " port " + args[1]);

			while (!end) {

				// get next consignment
				readBuffer = new byte[1000];
				DatagramPacket receivedPacket = new DatagramPacket(readBuffer, readBuffer.length);
				clientSocket.receive(receivedPacket);

				// Simulate network delay.
				try {
					Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));
				} catch (Exception e) {
					System.out.println(e);
				}

				int packetNum = readBuffer[4];
				int packetLength = receivedPacket.getLength();

				// if last consignment
				{
					String endMessage = new String(new byte[] { readBuffer[packetLength - 6],
							readBuffer[packetLength - 5], readBuffer[packetLength - 4] });

					if (endMessage.equals("END")) {

						end = true;

					}
				}

				// handle out-of-order packets
				if (packetNum == expectedPacket) {

					System.out.println();
					System.out.println("Received CONSIGNMENT " + packetNum);

					System.out.println();
					System.out.println(new String(readBuffer));

					int payloadLength = packetLength - ((end) ? 13 : 9);

					if (payloadLength != 0) {

						byte[] payload = new byte[payloadLength];
						System.arraycopy(readBuffer, 6, payload, 0, payloadLength);
						fileOut.write(payload);

					}

					expectedPacket = increment(expectedPacket);

				} else {

					System.out.println();
					System.out.println("Received CONSIGNMENT " + packetNum + " duplicate - discarding");

				}

				if (end)

					expectedPacket = 0;

				// drop acknowledgement probabilistically
				// cannot drop END acknowledgement
				if (random.nextDouble() >= LOSS_RATE || end == true) {

					if (!end) {
						System.out.println();
						System.out.println("Sent ACK " + expectedPacket);
					}

					sendBuffer = getMessage(expectedPacket);
					sentPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivedPacket.getAddress(),
							receivedPacket.getPort());

					clientSocket.send(sentPacket);

				} else {

					System.out.println();
					System.out.println("Forgot ACK " + expectedPacket);

				}

			}

			System.out.println();
			System.out.println("END");

			try {
				Thread.sleep(500);
			} catch (Exception e) {
				System.out.println(e);
			}

		} catch (IOException e) {
			System.out.println(e);
			System.out.println(e.getStackTrace()[0].getLineNumber());

		} finally {

			try {
				if (fileOut != null)
					fileOut.close();
				if (clientSocket != null)
					clientSocket.close();
			} catch (IOException e) {
				System.out.println(e);
				System.out.println(e.getStackTrace()[0].getLineNumber());
			}
		}
	}

}
