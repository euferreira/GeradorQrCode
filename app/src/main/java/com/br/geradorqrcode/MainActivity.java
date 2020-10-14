package com.br.geradorqrcode;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
        implements View.OnClickListener {

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    BluetoothDevice bluetoothDevice;

    OutputStream outputStream;
    InputStream inputStream;
    Thread thread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    Bitmap bitmap = null;

    EditText editTexto;
    ImageView ivQRCode;
    Button btnGerar, btnabreoutratela;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inicializaComponente();

        try {
            findBluetoothDevice();
            openBluetoothPrinter();
        } catch (Exception e) {
            e.printStackTrace();
        }

        btnGerar.setOnClickListener(this);
        btnabreoutratela.setOnClickListener(this);
    }

    private void inicializaComponente() {
        editTexto = findViewById(R.id.edtTexto);
        btnGerar = findViewById(R.id.btnGerar);
        ivQRCode = findViewById(R.id.imageView);
        btnabreoutratela = findViewById(R.id.button);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnGerar) {
            gerarQrCode();
        } else if (v.getId() == R.id.button) {
            try {
                printData1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void gerarQrCode() {
        String texto = editTexto.getText().toString();
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();

        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(texto, BarcodeFormat.QR_CODE, 160, 175);
            //BitMatrix bitMatrix = multiFormatWriter.encode(texto, BarcodeFormat.EAN_13, 250, 250);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ivQRCode.setImageBitmap(bitmap);
            int trans = Color.TRANSPARENT;
            //ivQRCode.setBackgroundColor(Color.parseColor("#80000000"));


        } catch (WriterException e) {
            e.printStackTrace();
        }

    }

    void findBluetoothDevice() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                //lblPrinterName.setText("Não encontrou impressora bluetooth");
                Toast.makeText(this, "NÃO ENCONTROU IMPRESSORA", Toast.LENGTH_SHORT).show();
            }
            if (bluetoothAdapter.isEnabled()) {
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBT, 0);
            }

            Set<BluetoothDevice> pairedDevice = bluetoothAdapter.getBondedDevices();
            if (pairedDevice.size() > 0) {
                for (BluetoothDevice pairedDev : pairedDevice) {

                    String nomeImpressora = pairedDev.getName();
                    //nome da impressora do cara
                    if (pairedDev.getName().equals("BTP_F09F1A")) {
                        bluetoothDevice = pairedDev;
                        //lblPrinterName.setText("Bluetooth Printer attached: " + pairedDev.getName());
                        Toast.makeText(this, "Impressora conectada!", Toast.LENGTH_SHORT).show();
                        break;
                    } else if (pairedDev.getName().equals(nomeImpressora)) {
                        bluetoothDevice = pairedDev;
                        //lblPrinterName.setText("Bluetooth Printer attached: " + nomeImpressora);
                        Toast.makeText(this, "Impressora conectada: ", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
            //lblPrinterName.setText("Bluetooth Printer Attached");
            Toast.makeText(this, "Impressora conectada", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //OpenBluetoothPrinter
    void openBluetoothPrinter() throws IOException {
        try {
            //standar uuid from string
            UUID uuidString = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuidString);
            bluetoothSocket.connect();

            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();

            beginListenData();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    void beginListenData() {
        try {
            final Handler handler = new Handler();
            final byte delimater = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            thread = new Thread(new Runnable() {
                @Override
                public void run() {

                    while (!Thread.currentThread().isInterrupted() && stopWorker) {

                        try {
                            int byteAvailable = inputStream.available();
                            if (byteAvailable > 0) {
                                byte[] packetByte = new byte[byteAvailable];
                                inputStream.read(packetByte);

                                for (int i = 0; i < byteAvailable; i++) {
                                    byte b = packetByte[i];
                                    if (b == delimater) {
                                        byte[] encondedByte = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encondedByte, 0, encondedByte.length);
                                        final String data = new String(encondedByte, "US-ASCII");
                                        readBufferPosition = 0;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                //lblPrinterName.setText(data);
                                                Toast.makeText(MainActivity.this, data, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }

                            }


                        } catch (Exception e) {
                            e.printStackTrace();
                            stopWorker = true;
                        }

                    }

                }
            });
            thread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //mandando imprimir
    void printData() throws IOException {

        try {

            String mensagem = "\n------------------------------\n";
            mensagem += "\n------------------------------\n";
            mensagem += "\n------------------------------\n";
            mensagem += "\n------------------------------\n\n\n\n";


            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            outputStream.write(mensagem.getBytes());

            bitmap = ((BitmapDrawable) ivQRCode.getDrawable()).getBitmap();

            byte[] command = Utils.decodeBitmap(bitmap);
            //outputStream.write(PrinterCommands.ESC_ALIGN_RIGHT);
            outputStream.write(command);


            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            outputStream.write(mensagem.getBytes());
            outputStream.write(PrinterCommands.SET_LINE_SPACING_30);

            Toast.makeText(this, "Imprimindo..", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    void printData1() throws IOException{
        try {

            String mensagem = "\n------------------------------\n";
            mensagem += "\n------------------------------\n";
            mensagem += "\n------------------------------\n";
            mensagem += "\n------------------------------";

            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            outputStream.write(mensagem.getBytes());

            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            BitMatrix bitMatrix = multiFormatWriter.encode("Texto de teste", BarcodeFormat.QR_CODE, 160, 175);
            //BitMatrix bitMatrix = multiFormatWriter.encode(texto, BarcodeFormat.EAN_13, 250, 250);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            bitmap = barcodeEncoder.createBitmap(bitMatrix);

            //bitmap = ((BitmapDrawable) ivQRCode.getDrawable()).getBitmap();
            byte[] command = Utils.decodeBitmap(bitmap);
            outputStream.write(command);

            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            String conteudo = "Data: XX/XX/XXXX HH:MM:SS\n";
            conteudo += "Placa: XXX-XXXX\n";
            conteudo += "Modelo: XXXXXXXXX";
            outputStream.write(conteudo.getBytes());

            String rodape = "\n------------------------------\n";
            rodape += "APRESENTE NA SAIDA\n\n\n\n\n";
            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            outputStream.write(rodape.getBytes());

            outputStream.write(PrinterCommands.SET_LINE_SPACING_30);

            Toast.makeText(this, "Imprimindo..", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //disconnect printer
    void disconnectPrinter() throws IOException {
        try {
            stopWorker = true;
            outputStream.close();
            inputStream.close();
            bluetoothSocket.close();
            //lblPrinterName.setText("Printer disconnect");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------------

}
