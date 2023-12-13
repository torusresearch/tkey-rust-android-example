package com.example.tkey_android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.tkey_android.databinding.FragmentFirstBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import com.web3auth.tkey.RuntimeError;
import com.web3auth.tkey.ThresholdKey.Common.KeyPoint;
import com.web3auth.tkey.ThresholdKey.Common.PrivateKey;
import com.web3auth.tkey.ThresholdKey.Common.Result;
import com.web3auth.tkey.ThresholdKey.KeyDetails;
import com.web3auth.tkey.ThresholdKey.Modules.TSSModule;
import com.web3auth.tkey.ThresholdKey.RssComm;
import com.web3auth.tkey.ThresholdKey.ServiceProvider;
import com.web3auth.tkey.ThresholdKey.StorageLayer;
import com.web3auth.tkey.ThresholdKey.ThresholdKey;
import com.web3auth.web3_android_mpc_provider.CustomSigningError;
import com.web3auth.web3_android_mpc_provider.EthTssAccountParams;
import com.web3auth.web3_android_mpc_provider.EthereumTssAccount;
import com.web3auth.tss_client_android.client.TSSClientError;

import org.json.JSONException;
import org.json.JSONObject;
import org.torusresearch.customauth.CustomAuth;
import org.torusresearch.customauth.types.Auth0ClientOptions.Auth0ClientOptionsBuilder;
import org.torusresearch.customauth.types.CustomAuthArgs;
import org.torusresearch.customauth.types.LoginType;
import org.torusresearch.customauth.types.NoAllowedBrowserFoundException;
import org.torusresearch.customauth.types.SubVerifierDetails;
import org.torusresearch.customauth.types.TorusLoginResponse;
import org.torusresearch.customauth.types.UserCancelledException;
import org.torusresearch.customauth.utils.Helpers;
import org.torusresearch.fetchnodedetails.FetchNodeDetails;
import org.torusresearch.fetchnodedetails.types.NodeDetails;
import org.torusresearch.fetchnodedetails.types.TorusNetwork;
import org.torusresearch.torusutils.TorusUtils;
import org.torusresearch.torusutils.types.SessionToken;
import org.torusresearch.torusutils.types.TorusCtorOptions;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class TkeyMpcFragment extends Fragment {
    public static void showAlert(final Context context, final String message) {
        AlertDialog.Builder alertbox = new AlertDialog.Builder(context);
        alertbox.setCancelable(false);
        alertbox.setMessage(message);
        alertbox.setNeutralButton("OK", (dialog, which) -> dialog.dismiss());
        alertbox.show();
    }

    private static final String GOOGLE_CLIENT_ID = "221898609709-obfn3p63741l5333093430j3qeiinaa8.apps.googleusercontent.com";
    private static final String GOOGLE_VERIFIER = "google-lrc";
    private FragmentFirstBinding binding;
    private LoginVerifier selectedLoginVerifier;
    private CustomAuth torusSdk;

    static {
        System.loadLibrary("dkls-native");
    }

    private static final String PREF_NAME = "TKEY";
    private static final String STRING_KEY = "FactorKey";

    private final String[] allowedBrowsers = new String[]{
            "com.android.chrome", // Chrome stable
            "com.google.android.apps.chrome", // Chrome system
            "com.android.chrome.beta", // Chrome beta
    };

    private final String factor_key = "";
    private int tssNonce;
    private String tssShare = "";
    private String tssIndex = "";
    private String verifierId = "";
    private String verifier = "";
    private NodeDetails nodeDetail;
    private final AtomicReference<String> pubKey = new AtomicReference<>("");
    private ArrayList<String> signatureString;
    private Pair<String, String> tssShareResponse = new Pair<>("", "");
    private final Gson gson = new Gson();
    private String fullTssPubKey;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private void showLoading() {
        requireActivity().runOnUiThread(() -> {
            ProgressBar pb = binding.loadingIndicator;
            pb.setVisibility(View.VISIBLE);
        });
    }

    private void hideLoading() {
        requireActivity().runOnUiThread(() -> {
            ProgressBar pb = binding.loadingIndicator;
            pb.setVisibility(View.GONE);
        });
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = ((MainActivity) requireActivity());

        userHasNotLoggedInWithGoogle();

        CustomAuthArgs args = new CustomAuthArgs(
                "https://scripts.toruswallet.io/redirect.html",
                TorusNetwork.SAPPHIRE_MAINNET,
                "torusapp://org.torusresearch.customauthandroid/redirect",
                "BPi5PB_UiIZ-cPz1GtV5i1I2iOSOHuimiXBI0e-Oe_u6X3oVAbCiAZOTEBtTXw4tsluTITPqA8zMsfxIKMjiqNQ"
        );
        args.setEnableOneKey(true);

        // Initialize CustomAuth
        this.torusSdk = new CustomAuth(args, activity);

        binding.googleLogin.setOnClickListener(view1 -> {
            showLoading();
            try {
                selectedLoginVerifier = new LoginVerifier("Google", LoginType.GOOGLE, GOOGLE_CLIENT_ID, GOOGLE_VERIFIER);

                Auth0ClientOptionsBuilder builder = null;
                if (selectedLoginVerifier.getDomain() != null) {
                    builder = new Auth0ClientOptionsBuilder(selectedLoginVerifier.getDomain());
                    builder.setVerifierIdField(selectedLoginVerifier.getVerifierIdField());
                    builder.setVerifierIdCaseSensitive(selectedLoginVerifier.isVerfierIdCaseSensitive());
                }
                CompletableFuture<TorusLoginResponse> torusLoginResponseCf;
                if (builder == null) {
                    torusLoginResponseCf = torusSdk.triggerLogin(new SubVerifierDetails(selectedLoginVerifier.getTypeOfLogin(),
                            selectedLoginVerifier.getVerifier(),
                            selectedLoginVerifier.getClientId())
                            .setPreferCustomTabs(true)
                            .setAllowedBrowsers(allowedBrowsers));
                } else {
                    torusLoginResponseCf = torusSdk.triggerLogin(new SubVerifierDetails(
                            selectedLoginVerifier.getTypeOfLogin(),
                            selectedLoginVerifier.getVerifier(),
                            selectedLoginVerifier.getClientId(),
                            builder.build())
                            .setPreferCustomTabs(true)
                            .setAllowedBrowsers(allowedBrowsers));
                }

                torusLoginResponseCf.whenCompleteAsync((torusLoginResponse, error) -> {
                    if (error != null) {
                        renderError(error);
                        hideLoading();
                    } else {
                        activity.runOnUiThread(() -> {
                            String publicAddress = torusLoginResponse.getPublicAddress();
                            activity.postboxKey = torusLoginResponse.getPrivateKey().toString(16);
                            activity.userInfo = torusLoginResponse.getUserInfo();
                            activity.sessionData = torusLoginResponse.getRetrieveSharesResponse().getSessionData();
                            binding.resultView.append("publicAddress: " + publicAddress);
                            userHasLoggedInWithGoogle();
                            hideLoading();
                        });

                    }
                });
            } catch (Exception e) {
                renderError(e);
            }
        });

        binding.createThresholdKey.setOnClickListener(view1 -> {
            showLoading();
            try {
                showLoading();

                // prepare tkey parameters
                verifierId = activity.userInfo.getVerifierId();
                verifier = activity.userInfo.getVerifier();

                List<SessionToken> sessionTokenData = activity.sessionData.getSessionTokenData();
                signatureString = new ArrayList<>();
                for (SessionToken item : sessionTokenData) {
                    if (item != null) {
                        LinkedHashMap<String, Object> msg = new LinkedHashMap<>();
                        msg.put("data", item.getToken());
                        msg.put("sig", item.getSignature());
                        String jsonData = gson.toJson(msg);
                        signatureString.add(jsonData);
                    }
                }

                // node details
                FetchNodeDetails nodeManager = new FetchNodeDetails(TorusNetwork.SAPPHIRE_MAINNET);
                CompletableFuture<NodeDetails> nodeDetailResult = nodeManager.getNodeDetails(verifier, verifierId);
                nodeDetail = nodeDetailResult.get();

                // Torus Utils
                TorusCtorOptions torusOptions = new TorusCtorOptions("Custom");
                torusOptions.setNetwork(TorusNetwork.SAPPHIRE_MAINNET.toString());
                torusOptions.setClientId("BPi5PB_UiIZ-cPz1GtV5i1I2iOSOHuimiXBI0e-Oe_u6X3oVAbCiAZOTEBtTXw4tsluTITPqA8zMsfxIKMjiqNQ");
                TorusUtils torusUtils = new TorusUtils(torusOptions);
                // String[] tssEndpoint = nodeDetail.getTorusNodeTSSEndpoints();
                RssComm rss_comm = new RssComm();

                activity.tkeyStorage = new StorageLayer(false, "https://metadata.tor.us", 2);
                activity.tkeyProvider = new ServiceProvider(false, activity.postboxKey,true, verifier, verifierId, nodeDetail);
                activity.tKey = new ThresholdKey(null, null, activity.tkeyStorage, activity.tkeyProvider, null, null, true, false, rss_comm);

                activity.tKey.initialize(activity.postboxKey, null, false, false, false, false, null, 0, null, result -> {
                    if (result instanceof Result.Error) {
                        throw new RuntimeException("Could not initialize tkey");
                    }
                    KeyDetails keyDetails;
                    //String metadataPublicKey;

                    try {
                        keyDetails = activity.tKey.getKeyDetails();
                        //metadataPublicKey = keyDetails.getPublicKeyPoint().getPublicKey(KeyPoint.PublicKeyEncoding.EllipticCompress);
                    } catch (RuntimeError e) {
                        throw new RuntimeException(e);
                    }


                    // existing or new user check
                    try {
                        if(keyDetails.getRequiredShares() > 0) {
                            // existing user
                            ArrayList<String> allTags = TSSModule.getAllTSSTags(activity.tKey);
                            String tag = allTags.get(0); //"default";
                            // String fetchId = metadataPublicKey + ":" + tag + ":0";

                            // fetch key from keystore and assign it to factorKey
                            String factorKey;
                            String retrievedKey = getStringFromSharedPreferences();

                            if (retrievedKey != null) {
                                factorKey = retrievedKey;
                            } else {
                                throw new RuntimeException("factor key not found in storage");
                            }

                            // input factor key from key store
                            activity.tKey.inputFactorKey(factorKey, inputFactorResult -> {
                                if (inputFactorResult instanceof Result.Error) {
                                    throw new RuntimeException("Could not inputFactorKey for tkey");
                                }
                                PrivateKey pk = new PrivateKey(factorKey);
                                try {
                                    pk.toPublic(KeyPoint.PublicKeyEncoding.FullAddress);
                                } catch (RuntimeError e) {
                                    throw new RuntimeException(e);
                                }

                                // reconstruct and getTssPubKey
                                activity.tKey.reconstruct(_reconstructResult -> {
                                    if (_reconstructResult instanceof Result.Error) {
                                        throw new RuntimeException("Could not reconstruct tkey");
                                    }
                                    try {
                                        activity.tKey.getKeyDetails();
                                    } catch (RuntimeError e) {
                                        throw new RuntimeException(e);
                                    }

                                    TSSModule.getTSSPubKey(activity.tKey, tag, tssPubResult -> {
                                        if (tssPubResult instanceof Result.Error) {
                                            throw new RuntimeException("Could not getTSSPubKey tkey");
                                        }
                                        pubKey.set(((Result.Success<String>) tssPubResult).data);

                                        try {
                                            KeyPoint keyPoint = new KeyPoint(pubKey.get());
                                            fullTssPubKey = keyPoint.getPublicKey(KeyPoint.PublicKeyEncoding.FullAddress);
                                        } catch (RuntimeError e) {
                                            throw new RuntimeException(e);
                                        }
                                        try {
                                            activity.tKey.getShareDescriptions();
                                        } catch (RuntimeError | JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });

                                    try {
                                        tssNonce = TSSModule.getTSSNonce(activity.tKey, tag, false);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }

                                    TSSModule.getTSSShare(activity.tKey, tag, factorKey, 0, getTSSShareResult -> {
                                        if (getTSSShareResult instanceof Result.Error) {
                                            System.out.println("could not get tss share");
                                            throw new RuntimeException("could not get tss share");
                                        }
                                        tssShareResponse = ((Result.Success<Pair<String, String>>) getTSSShareResult).data;
                                        tssShare = tssShareResponse.second;
                                        tssIndex = tssShareResponse.first;
                                    });
                                });
                            });
                            userHasCreatedTkey();
                            hideLoading();
                            binding.resultView.append("Log: \n");
                            binding.resultView.append("Tkey Creaetion Successfull" + "\n");
                        } else {
                            // new user
                            // check if reconstruction is working before creating tagged share
                            try {

                                int requiredShares = keyDetails.getRequiredShares();
                                activity.tKey.reconstruct(reconResultInit -> {
                                    if (reconResultInit instanceof Result.Error) {
                                        String errorMsg = "Failed to reconstruct key" + requiredShares  + " more share(s) required. If you have security question share, we suggest you to enter security question PW to recover your account";
                                        throw new RuntimeException(errorMsg);
                                    }

                                    // create tagged tss share
                                    PrivateKey factorKey;
                                    String factorPub;
                                    try {
                                        factorKey = PrivateKey.generate();
                                        factorPub = factorKey.toPublic(KeyPoint.PublicKeyEncoding.FullAddress);
                                    } catch (RuntimeError e) {
                                        throw new RuntimeException(e);
                                    }
                                    String defaultTag = "default";

                                    //                    re("factorPub", factorPub);
                                    PrivateKey finalFactorKey = factorKey;
                                    try {
                                        TSSModule.createTaggedTSSTagShare(activity.tKey, defaultTag, null, factorPub, 2, nodeDetail, torusUtils, createTaggedResult -> {
                                            if (createTaggedResult instanceof Result.Error) {
                                                throw new RuntimeException("Could not createTaggedTSSTagShare tkey");
                                            }
                                            AtomicReference<String> pubKeyNew = new AtomicReference<>("");
                                            TSSModule.getTSSPubKey(activity.tKey, defaultTag, getTSSPubKeyResult -> {
                                                if (getTSSPubKeyResult instanceof Result.Error) {
                                                    throw new RuntimeException("Could not getTSSPubKey tkey");
                                                }
                                                pubKeyNew.set(((Result.Success<String>) getTSSPubKeyResult).data);

                                                // backup share with input factor key
                                                ArrayList<String> shareIndexes;
                                                JSONObject description = new JSONObject();

                                                try {
                                                    shareIndexes = activity.tKey.getShareIndexes();

                                                    shareIndexes.removeIf(index -> index.equals("1"));
                                                    TSSModule.backupShareWithFactorKey(activity.tKey, shareIndexes.get(0), finalFactorKey.hex);

                                                    // add share description
                                                        description.put("module", "Device Factor key");
                                                        description.put("tssTag", defaultTag);
                                                        description.put("tssShareIndex", 2);
                                                        description.put("dateAdded", System.currentTimeMillis()/1000);
                                                } catch (RuntimeError | Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                                activity.tKey.addShareDescription(shareIndexes.get(0), description.toString(), true, addShareResult -> {
                                                    if (addShareResult instanceof Result.Error) {
                                                        throw new RuntimeException("Could not add share description for tkey");
                                                    }

                                                    saveStringToSharedPreferences(finalFactorKey.hex);

                                                    // reconstruction
                                                    activity.tKey.reconstruct(reconstructResult -> {
                                                        if (reconstructResult instanceof Result.Error) {
                                                            String errorMsg = "Failed to reconstruct key" + requiredShares  + " more share(s) required. If you have security question share, we suggest you to enter security question PW to recover your account";
                                                            throw new RuntimeException(errorMsg);
                                                        }
                                                        try {
                                                            tssNonce = TSSModule.getTSSNonce(activity.tKey, defaultTag, false);
                                                        } catch (Exception e) {
                                                            throw new RuntimeException(e);
                                                        }


                                                        TSSModule.getTSSShare(activity.tKey, defaultTag, factorKey.hex, 0, res -> {
                                                            if (res  instanceof Result.Error) {
                                                                String errorMsg = "Could not create tagged tss shares for tkey";
                                                                throw new RuntimeException(errorMsg);
                                                            }
                                                            tssShareResponse = ((Result.Success<Pair<String, String>>) res).data;
                                                            tssShare = tssShareResponse.second;
                                                            tssIndex = tssShareResponse.first;
                                                        });
                                                        // disable button
                                                        userHasCreatedTkey();
                                                        hideLoading();
                                                        binding.resultView.append("Log: \n");
                                                        binding.resultView.append("Tkey Creaetion Successfull" + "\n");
                                                    });
                                                });
                                            });
                                        });
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });

                            } catch (Exception | RuntimeError e) {
                                showAlert(activity, "Error: " + e.getMessage());
                                hideLoading();
                                renderError(e);
                            }
                        }
                    } catch (RuntimeError | JSONException e) {
                        showAlert(activity, "Error: " + e.getMessage());
                        throw new RuntimeException(e);
                    }

                });
            } catch (Exception | RuntimeError e) {
                showAlert(activity, "Error: " + e.getMessage());
                hideLoading();
                renderError(e);
            }
        });

        binding.resetAccount.setOnClickListener(view1 -> {
            try {
                // delete locally stored share
                StorageLayer temp_sl = new StorageLayer(false, "https://metadata.tor.us", 2);
                ServiceProvider temp_sp = new ServiceProvider(false, activity.postboxKey,true, null, null, null);
                ThresholdKey temp_key = new ThresholdKey(null, null, temp_sl, temp_sp, null, null, true, false, null);

                activity.sharedpreferences.edit().clear().apply();

                temp_key.storage_layer_set_metadata(activity.postboxKey, "{ \"message\": \"KEY_NOT_FOUND\" }", result -> {
                    if (result instanceof Result.Error) {
                        activity.runOnUiThread(() -> {
                            Exception e = ((Result.Error<Void>) result).exception;
                            renderError(e);
                        });
                    } else if (result instanceof Result.Success) {
                        activity.runOnUiThread(() -> {
                            activity.resetState();
                            userHasNotLoggedInWithGoogle();
                            binding.resultView.setText("");
                            Snackbar snackbar = Snackbar.make(view1, "Account reset successful", Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });

                    }
                });

                activity.postboxKey = null;

            } catch (RuntimeError | JSONException e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.getMessage(), Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.tssSignMessage.setOnClickListener(_view -> {
            showLoading();
            new Thread(() -> {
                EthTssAccountParams params = new EthTssAccountParams(
                        fullTssPubKey,
                        factor_key,
                        tssNonce,
                        tssShare,
                        tssIndex,
                        "default",
                        verifier,
                        verifierId,
                        nodeDetail.getTorusIndexes(),
                        nodeDetail.getTorusNodeTSSEndpoints(),
                        signatureString.toArray(new String[0])
                );

                EthereumTssAccount account = new EthereumTssAccount(params);
                String message = "hello world";
                try {
                    String sig = account.signMessage(message);
                    hideLoading();
                    requireActivity().runOnUiThread(() -> showAlert(activity, "Signature: " + sig));
                } catch (CustomSigningError | TSSClientError e) {
                    hideLoading();
                    requireActivity().runOnUiThread(() -> showAlert(activity, "Error: " + e.getMessage()));
                    throw new RuntimeException(e);
                }
            }).start();
        });

        binding.tssSignTransaction.setOnClickListener(_view -> {
                showLoading();
                new Thread(() -> {
                    try {
                        EthTssAccountParams params = new EthTssAccountParams(
                                fullTssPubKey,
                                factor_key,
                                tssNonce,
                                tssShare,
                                tssIndex,
                                "default",
                                verifier,
                                verifierId,
                                nodeDetail.getTorusIndexes(),
                                nodeDetail.getTorusNodeTSSEndpoints(),
                                signatureString.toArray(new String[0])
                        );

                        EthereumTssAccount account = new EthereumTssAccount(params);
                        BigInteger gasLimit = BigInteger.valueOf(21000);
                        String toAddress = "0xE09543f1974732F5D6ad442dDf176D9FA54a5Be0";

                        String url = "https://rpc.ankr.com/eth_goerli";
                        Web3j web3j = Web3j.build(new HttpService(url));
                        String signedTransaction = account.signLegacyTransaction(web3j, toAddress, 0.001, null, gasLimit);
                         EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTransaction).send();
                         if (ethSendTransaction.getError() != null) {
                            requireActivity().runOnUiThread(() -> showAlert(activity, "Error: " + ethSendTransaction.getError().getMessage()));
                         } else {
                            requireActivity().runOnUiThread(() -> showAlert(activity, "TransactionHash: " + ethSendTransaction.getTransactionHash()));
                         }
                        hideLoading();
                    } catch (Exception e) {
                        hideLoading();
                        requireActivity().runOnUiThread(() -> showAlert(activity, "Error: " + e.getMessage()));
                        throw new RuntimeException(e);
                    }
                }).start();
        });
    }

    private void renderError(Throwable error) {
        requireActivity().runOnUiThread(() -> {
            Throwable reason = Helpers.unwrapCompletionException(error);
            TextView textView = binding.resultView;
            if (reason instanceof UserCancelledException || reason instanceof NoAllowedBrowserFoundException) {
                textView.setText(error.getMessage());
            } else {
                String errorMessage = getResources().getString(R.string.error_message, error.getMessage());
                textView.setText(errorMessage);
            }
        });
    }


    private void saveStringToSharedPreferences(String data) {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(STRING_KEY, data);
        editor.apply();
    }

    private String getStringFromSharedPreferences() {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(STRING_KEY, null);
    }

    private void userHasNotLoggedInWithGoogle() {
        requireActivity().runOnUiThread(() -> {
            binding.googleLogin.setEnabled(true);
            binding.createThresholdKey.setEnabled(false);
            binding.resetAccount.setEnabled(false);
            binding.tssSignMessage.setEnabled(false);
            binding.tssSignTransaction.setEnabled(false);
        });
    }

    private void userHasLoggedInWithGoogle() {
        requireActivity().runOnUiThread(() -> {
            binding.googleLogin.setEnabled(false);
            binding.createThresholdKey.setEnabled(true);
            binding.resetAccount.setEnabled(true);
            binding.tssSignMessage.setEnabled(false);
            binding.tssSignTransaction.setEnabled(false);
        });
    }

    private void userHasCreatedTkey() {
        requireActivity().runOnUiThread(() -> {
            binding.googleLogin.setEnabled(false);
            binding.createThresholdKey.setEnabled(false);
            binding.resetAccount.setEnabled(true);
            binding.tssSignMessage.setEnabled(true);
            binding.tssSignTransaction.setEnabled(true);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}