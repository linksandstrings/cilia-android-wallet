package com.cilia.wallet.paymentrequest;

import android.os.AsyncTask;
import android.os.Build;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitillib.model.BitcoinILTransaction;
import com.mrd.bitlib.model.BitcoinAddress;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.model.BitcoinTransaction;
import com.mycelium.paymentrequest.PaymentRequestException;
import com.mycelium.paymentrequest.PaymentRequestInformation;
import com.mycelium.wapi.content.AssetUri;
import com.mycelium.wapi.content.WithCallback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.otto.Bus;
import com.squareup.wire.Wire;

import org.bitcoin.protocols.payments.Payment;
import org.bitcoin.protocols.payments.PaymentACK;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class PaymentRequestHandler {
   public static final String MIME_PAYMENTREQUEST = "application/bitcoin-paymentrequest";
   public static final String MIME_ACK = "application/bitcoin-paymentack";

   private final Bus eventBus;
   private final NetworkParameters networkParameters;
   private PaymentRequestInformation paymentRequestInformation;
   private String merchantMemo;

   public PaymentRequestHandler(Bus eventBus, NetworkParameters networkParameters) {
      this.eventBus = eventBus;
      this.networkParameters = networkParameters;
   }

   public void fetchPaymentRequest(final AssetUri uri) {
      if (hasValidPaymentRequest()) {
         // dont refresh from the server, if we already have fetched it
         eventBus.post(paymentRequestInformation);
      } else {
         new AsyncTask<Void, Void, AsyncResultRequest>() {
            @Override
            protected AsyncResultRequest doInBackground(Void... params) {
               try {
                  PaymentRequestInformation paymentRequestInformation = fromBitcoinUri(uri);
                  return new AsyncResultRequest(paymentRequestInformation);
               } catch (PaymentRequestException ex) {
                  return new AsyncResultRequest(ex);
               }
            }

            @Override
            protected void onPostExecute(AsyncResultRequest result) {
               super.onPostExecute(result);
               if (result.exception != null) {
                  eventBus.post(result.exception);
               } else {
                  paymentRequestInformation = result.requestInformation;
                  eventBus.post(paymentRequestInformation);
               }
            }
         }.execute();
      }
   }

   // parse it from already received data and call events just like if we got it from an http call
   public void parseRawPaymentRequest(final byte[] rawPr) {
      if (hasValidPaymentRequest()) {
         // dont refresh from the server, if we already have fetched it
         eventBus.post(paymentRequestInformation);
      } else {
         try {
            paymentRequestInformation = PaymentRequestInformation.fromRawPaymentRequest(rawPr, getAndroidKeyStore(), networkParameters);
            eventBus.post(paymentRequestInformation);
         } catch (PaymentRequestException ex) {
            eventBus.post(ex);
         }
      }
   }

   public PaymentRequestInformation getPaymentRequestInformation() {
      return paymentRequestInformation;
   }

   public boolean hasValidPaymentRequest() {
      return paymentRequestInformation != null;
   }

   class AsyncResultRequest {
      public final PaymentRequestException exception;
      public final PaymentRequestInformation requestInformation;

      public AsyncResultRequest(PaymentRequestException exception) {
         this.exception = exception;
         requestInformation = null;
      }

      public AsyncResultRequest(PaymentRequestInformation requestInformation) {
         this.requestInformation = requestInformation;
         exception = null;
      }
   }


   public PaymentRequestInformation fromBitcoinUri(AssetUri assetUri) {
      WithCallback withCallback = (WithCallback) assetUri;
      Preconditions.checkNotNull(withCallback.getCallbackURL());
      Preconditions.checkArgument(!Strings.isNullOrEmpty(withCallback.getCallbackURL()));

      // try to get the payment request from the server
      PaymentRequestInformation paymentRequestInformation = fromCallback(withCallback.getCallbackURL());

      // if the BIP21-URI has an amount specified, check it if it matches the payment-request amount
      boolean hasBip21Amount = assetUri.getValue() != null && assetUri.getValue().isPositive();
      boolean hasBip70Amount = paymentRequestInformation.hasAmount();
      if (hasBip21Amount && hasBip70Amount) {
         final long totalAmount = paymentRequestInformation.getOutputs().getTotalAmount();
         if (assetUri.getValue().getValueAsLong() != totalAmount) {
            throw new PaymentRequestException(String.format("Uri amount does not match payment request amount, %d vs. %d", assetUri.getValue().value, totalAmount));
         }
      }
      return paymentRequestInformation;
   }

   // this method can be overwritten to change settings of the http client (eg. tor, ...)
   protected OkHttpClient getHttpClient(){
      return new OkHttpClient();
   }

   public PaymentRequestInformation fromCallback(String callbackURL) {
      URL url;
      url = checkUrl(callbackURL);

      Request request = new Request.Builder()
            .url(url)
            .addHeader("Accept", MIME_PAYMENTREQUEST)
            .build();

      try {
         final OkHttpClient httpClient;
         httpClient = getHttpClient();

         Response response = httpClient.newCall(request).execute();

         if (response.isSuccessful()) {
            if (!response.body().contentType().toString().equals(MIME_PAYMENTREQUEST)) {
               throw new PaymentRequestException("server responded with wrong mime-type");
            }
            byte[] data = response.body().bytes();
            return PaymentRequestInformation.fromRawPaymentRequest(data, getAndroidKeyStore(), networkParameters);
         } else {
            throw new PaymentRequestException("could not fetch the payment request from " + url.toString());
         }

      } catch (IOException e) {
         throw new PaymentRequestException("server did not respond", e);
      }
   }

   private URL checkUrl(String urlString) {
      URL url;
      try {
         url = new URL(urlString);
         if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
            throw new PaymentRequestException("invalid protocol");
         }
      } catch (MalformedURLException e) {
         throw new PaymentRequestException("invalid url");
      }
      return url;
   }


   private static KeyStore getAndroidKeyStore() {
      KeyStore trustStore;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
         try {
            trustStore = KeyStore.getInstance("AndroidCAStore");
            trustStore.load(null, null);
         } catch (KeyStoreException e) {
            throw new PaymentRequestException("unable to access keystore", e);
         } catch (CertificateException e) {
            throw new PaymentRequestException("unable to access keystore", e);
         } catch (NoSuchAlgorithmException e) {
            throw new PaymentRequestException("unable to access keystore", e);
         } catch (IOException e) {
            throw new PaymentRequestException("unable to access keystore", e);
         }
      } else {
         // we have minSdk == ICS
         throw new PaymentRequestException("unsupported keystore");
      }

      return trustStore;
   }

   public boolean sendResponse(final BitcoinTransaction signedTransaction, final BitcoinAddress refundAddress) {
      if (hasValidPaymentRequest() && !Strings.isNullOrEmpty(paymentRequestInformation.getPaymentDetails().payment_url)) {
         new AsyncTask<Void, Void, AsyncResultAck>() {
            @Override
            protected AsyncResultAck doInBackground(Void... params) {
               Payment payment = getPaymentRequestInformation().buildPaymentResponse(refundAddress, merchantMemo, signedTransaction);
               try {
                  PaymentACK paymentAck = sendPaymentResponse(payment);
                  return new AsyncResultAck(paymentAck);
               } catch (PaymentRequestException ex) {
                  return new AsyncResultAck(ex);
               }
            }

            @Override
            protected void onPostExecute(AsyncResultAck paymentACK) {
               if (paymentACK.exception != null) {
                  eventBus.post(paymentACK.exception);
               } else {
                  eventBus.post(paymentACK.paymentAck);
               }
            }
         }.execute();
         return true;
      } else {
         return false;
      }
   }

   public boolean sendBitcoinILResponse(final BitcoinILTransaction signedTransaction, final BitcoinILAddress refundAddress) {
      if (hasValidPaymentRequest() && !Strings.isNullOrEmpty(paymentRequestInformation.getPaymentDetails().payment_url)) {
         new AsyncTask<Void, Void, AsyncResultAck>() {
            @Override
            protected AsyncResultAck doInBackground(Void... params) {
               Payment payment = getPaymentRequestInformation().buildBitcoinILPaymentResponse(refundAddress, merchantMemo, signedTransaction);
               try {
                  PaymentACK paymentAck = sendPaymentResponse(payment);
                  return new AsyncResultAck(paymentAck);
               } catch (PaymentRequestException ex) {
                  return new AsyncResultAck(ex);
               }
            }

            @Override
            protected void onPostExecute(AsyncResultAck paymentACK) {
               if (paymentACK.exception != null) {
                  eventBus.post(paymentACK.exception);
               } else {
                  eventBus.post(paymentACK.paymentAck);
               }
            }
         }.execute();
         return true;
      } else {
         return false;
      }
   }

   public void setMerchantMemo(String memo) {
      merchantMemo = memo;
   }

   class AsyncResultAck {
      public final PaymentACK paymentAck;
      public final PaymentRequestException exception;

      public AsyncResultAck(PaymentRequestException exception) {
         this.exception = exception;
         paymentAck = null;
      }

      public AsyncResultAck(PaymentACK paymentAck) {
         this.paymentAck = paymentAck;
         exception = null;
      }
   }


   private PaymentACK sendPaymentResponse(Payment payment) {
      RequestBody requestBody = RequestBody.create(MediaType.parse("application/bitcoin-payment"), payment.toByteArray());

      URL url = checkUrl(paymentRequestInformation.getPaymentDetails().payment_url);

      Request request = new Request.Builder()
            .url(url)
            .addHeader("Accept", MIME_ACK)
            .post(requestBody)
            .build();


      try {
         final OkHttpClient httpClient;
         httpClient = getHttpClient();
         Response response = httpClient.newCall(request).execute();

         Wire wire = new Wire();

         PaymentACK paymentAck;
         if (response.isSuccessful()) {

            if (!response.body().contentType().toString().equals(MIME_ACK)) {
               throw new PaymentRequestException("server responded with wrong ack mime-type");
            }

            byte[] data = response.body().bytes();
            if (data.length > PaymentRequestInformation.MAX_MESSAGE_SIZE) {
               throw new PaymentRequestException("ack-message too large");
            }
            paymentAck = wire.parseFrom(data, PaymentACK.class);
            if (paymentAck == null){
               throw new PaymentRequestException("could not parse the returned ACK from " + paymentRequestInformation.getPaymentDetails().payment_url);
            }
            return paymentAck;
         } else {
            throw new PaymentRequestException(String.format("could not send the payment to %s (HTTP %d)", paymentRequestInformation.getPaymentDetails().payment_url, response.code()));
         }


      } catch (IOException e) {
         throw new PaymentRequestException("server did not respond with an payment ack", e);
      }

   }

}

