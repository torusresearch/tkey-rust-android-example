package com.example.tkey_android;
import android.content.SharedPreferences;
import android.util.Log;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.tkey_android.databinding.FragmentFirstBinding;
import com.google.android.material.snackbar.Snackbar;
import com.web3auth.tkey.RuntimeError;
import com.web3auth.tkey.ThresholdKey.Common.PrivateKey;
import com.web3auth.tkey.ThresholdKey.Common.Result;
import com.web3auth.tkey.ThresholdKey.GenerateShareStoreResult;
import com.web3auth.tkey.ThresholdKey.KeyDetails;
import com.web3auth.tkey.ThresholdKey.KeyReconstructionDetails;
import com.web3auth.tkey.ThresholdKey.Modules.PrivateKeysModule;
import com.web3auth.tkey.ThresholdKey.Modules.SecurityQuestionModule;
import com.web3auth.tkey.ThresholdKey.Modules.SeedPhraseModule;
import com.web3auth.tkey.ThresholdKey.Modules.ShareSerializationModule;
import com.web3auth.tkey.ThresholdKey.ServiceProvider;
import com.web3auth.tkey.ThresholdKey.StorageLayer;
import com.web3auth.tkey.ThresholdKey.ThresholdKey;

import org.json.JSONException;
import org.torusresearch.customauth.CustomAuth;
import org.torusresearch.customauth.types.Auth0ClientOptions.Auth0ClientOptionsBuilder;
import org.torusresearch.customauth.types.CustomAuthArgs;
import org.torusresearch.customauth.types.LoginType;
import org.torusresearch.customauth.types.NoAllowedBrowserFoundException;
import org.torusresearch.customauth.types.SubVerifierDetails;
import org.torusresearch.customauth.types.TorusLoginResponse;
import org.torusresearch.customauth.types.UserCancelledException;
import org.torusresearch.customauth.utils.Helpers;
import org.torusresearch.fetchnodedetails.types.TorusNetwork;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FirstFragment extends Fragment {

    private static final String GOOGLE_CLIENT_ID = "221898609709-obfn3p63741l5333093430j3qeiinaa8.apps.googleusercontent.com";
    private static final String GOOGLE_VERIFIER = "google-lrc";
    private FragmentFirstBinding binding;
    private LoginVerifier selectedLoginVerifier;
    private CustomAuth torusSdk;

    private final String[] allowedBrowsers = new String[]{
            "com.android.chrome", // Chrome stable
            "com.google.android.apps.chrome", // Chrome system
            "com.android.chrome.beta", // Chrome beta
    };

//    To be used for saving/reading data from shared prefs
    private final String POSTBOX_KEY_ALIAS = "POSTBOX_KEY";
    private final String SHARE_ALIAS = "SHARE";

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

     private void displayError(Exception e, String context, View view) {
        requireActivity().runOnUiThread(() -> {
            Snackbar snackbar = Snackbar.make(view, "A problem occurred in " + context + " : "  + e.toString(), Snackbar.LENGTH_LONG);
            snackbar.show();
        });
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = ((MainActivity) requireActivity());

        if (activity.appKey != null) {
            binding.reconstructThresholdKey.setEnabled(false);
            binding.createThresholdKey.setEnabled(true);

        } else {
            binding.createThresholdKey.setEnabled(true);
            binding.reconstructThresholdKey.setEnabled(false);
        }
        CustomAuthArgs args = new CustomAuthArgs("https://scripts.toruswallet.io/redirect.html", TorusNetwork.TESTNET, "torusapp://org.torusresearch.customauthandroid/redirect");

        // Initialize CustomAuth
        this.torusSdk = new CustomAuth(args, (((MainActivity) requireActivity())));

        binding.createThresholdKey.setEnabled(false);
        binding.generateNewShare.setEnabled(false);
        binding.deleteShare.setEnabled(false);
        binding.deleteSeedPhrase.setEnabled(false);

        binding.buttonFirst.setOnClickListener(view1 -> NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment));

        binding.googleLogin.setOnClickListener(view1 -> {
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

                torusLoginResponseCf.whenComplete((torusLoginResponse, error) -> activity.runOnUiThread(() -> {
                    if (error != null) {
                        renderError(error);
                    } else {
                        String publicAddress = torusLoginResponse.getPublicAddress();
                        activity.postboxKey = torusLoginResponse.getPrivateKey().toString(16);
                        binding.resultView.setText("publicAddress: " + publicAddress);
                        binding.createThresholdKey.setEnabled(true);
                        binding.googleLogin.setEnabled(false);
                    }
                }));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        binding.createThresholdKey.setOnClickListener(view1 -> {
//            Logic:
//            1. Fetch locally available shares and postbox key. Generate a new postboxkey if not available.
//            2. If no shares, then assume new user and try initialize and reconstruct. If success, save share, if fail prompt to reset account.
//            3. If shares are found, insert them into tkey and then try reconstruct. If success, all good, if fail then share is incorrect, go to prompt to reset account

            if(activity.postboxKey == null){
                String savedPostBoxKey = activity.sharedpreferences.getString(POSTBOX_KEY_ALIAS, null);
                if (savedPostBoxKey == null) {
                    try {
                        activity.postboxKey = PrivateKey.generate().hex;
                    } catch (RuntimeError e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        SharedPreferences.Editor editor = activity.sharedpreferences.edit();
                        editor.putString(POSTBOX_KEY_ALIAS, activity.postboxKey);
                        editor.commit();
                    } catch (RuntimeException e) {
                        Log.e("MainActivity", "failed to save postbox key");
                    }
                } else {
                    activity.postboxKey = savedPostBoxKey;
                }
            }



            try {
                activity.tkeyStorage = new StorageLayer(false, "https://metadata.tor.us", 2);
                activity.tkeyProvider = new ServiceProvider(false, activity.postboxKey);
                activity.appKey = new ThresholdKey(null, null, activity.tkeyStorage, activity.tkeyProvider, null, null, false, false);

//            1. Fetch locally available share
                String share = activity.sharedpreferences.getString(SHARE_ALIAS, null);
                activity.appKey.initialize(activity.postboxKey, null, false, false, result -> {
                        if (result instanceof Result.Error) {
                            Exception ee = ((Result.Error<KeyDetails>) result).exception;
                            ee.printStackTrace();
                            displayError(ee, "initializing tkey", view1);
                        } else if (result instanceof Result.Success) {
                            KeyDetails details = ((Result.Success<KeyDetails>) result).data;
                            if (share == null) {
                                // 2. If no shares, then assume new user and try initialize and reconstruct. If success, save share, if fail prompt to reset account
                                activity.appKey.reconstruct(reconstruct_result -> {
                                    if (reconstruct_result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                                        displayError((((Result.Error<KeyReconstructionDetails>) reconstruct_result).exception), "reconstructing key", view1);
                                    } else if (reconstruct_result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                                        KeyReconstructionDetails reconstructionDetails = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<KeyReconstructionDetails>) reconstruct_result).data;
                                        requireActivity().runOnUiThread(() -> {
                                            try {
                                                binding.resultView.setText("");
                                                binding.resultView.append("Final Key\n");
                                                binding.resultView.append(reconstructionDetails.getKey() + "\n");
                                                binding.resultView.append("Total Shares" + details.getTotalShares() + "\n");
                                                binding.resultView.append("Required Shares" + details.getThreshold() + "\n");
                                                binding.createThresholdKey.setEnabled(true);
                                                binding.reconstructThresholdKey.setEnabled(true);

                                                // Persist the share
                                                List<String> filters = new ArrayList<>();
                                                filters.add("1");
                                                ArrayList<String> indexes = activity.appKey.getShareIndexes();
                                                indexes.removeAll(filters);
                                                String index = indexes.get(0);
                                                String shareToSave = activity.appKey.outputShare(index, null);
                                                SharedPreferences.Editor editor = activity.sharedpreferences.edit();
                                                editor.putString(SHARE_ALIAS, shareToSave);
                                                editor.commit();
                                            } catch (RuntimeError | JSONException e) {
                                                Snackbar snackbar = Snackbar.make(view1, "Please reset account, a problem occurred: " + e, Snackbar.LENGTH_LONG);
                                                snackbar.show();
                                            }
                                        });
                                    }
                                });
                            } else {
                                // 3. If shares are found, insert them into tkey and then try reconstruct. If success, all good, if fail then share is incorrect, go to prompt to reset account
                                activity.appKey.inputShare(share, null, input_share_result -> {
                                    if(input_share_result instanceof Result.Error) {
                                        displayError(((Result.Error<Void>) input_share_result).exception, "input share", view1);
                                    } else if (input_share_result instanceof Result.Success) {
                                        activity.appKey.reconstruct(reconstruct_result_after_import -> {
                                            if(reconstruct_result_after_import instanceof Result.Error) {
                                                displayError(((Result.Error<KeyReconstructionDetails>) reconstruct_result_after_import).exception, "", view1);
                                            } else if(reconstruct_result_after_import instanceof Result.Success) {
                                                KeyReconstructionDetails reconstructionDetails = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<KeyReconstructionDetails>) reconstruct_result_after_import).data;
                                                requireActivity().runOnUiThread(() -> {
                                                    try {
                                                        binding.generateNewShare.setEnabled(true);
                                                        binding.resultView.setText("");
                                                        binding.resultView.append("Final Key\n");
                                                        binding.resultView.append(reconstructionDetails.getKey() + "\n");
                                                        binding.resultView.append("Total Shares" + details.getTotalShares() + "\n");
                                                        binding.resultView.append("Required Shares" + details.getThreshold() + "\n");
                                                        binding.createThresholdKey.setEnabled(false);
                                                        binding.reconstructThresholdKey.setEnabled(true);
                                                    } catch (RuntimeError e) {
                                                        Snackbar snackbar = Snackbar.make(view1, "Please reset account, a problem occurred: " + e, Snackbar.LENGTH_LONG);
                                                        snackbar.show();
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    });
            } catch (RuntimeError | RuntimeException e) {
                e.printStackTrace();
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.reconstructThresholdKey.setOnClickListener(view1 -> activity.appKey.reconstruct(result -> {
            if (result instanceof Result.Error) {
                displayError(((Result.Error<KeyReconstructionDetails>) result).exception, "reconstruct key", view1);
            } else if (result instanceof Result.Success) {
                requireActivity().runOnUiThread(() -> {
                    try {
                        KeyReconstructionDetails details = ((Result.Success<KeyReconstructionDetails>) result).data;
                        binding.generateNewShare.setEnabled(true);
                        Snackbar snackbar = Snackbar.make(view1, details.getKey(), Snackbar.LENGTH_LONG);
                        snackbar.show();
                    } catch (RuntimeError e) {
                        Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            }
        }));

        binding.generateNewShare.setOnClickListener(view1 -> {
            try {
                activity.appKey.generateNewShare(result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<GenerateShareStoreResult>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                GenerateShareStoreResult share = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<GenerateShareStoreResult>) result).data;
                                binding.deleteShare.setEnabled(true);
                                Snackbar snackbar = Snackbar.make(view1, share.getIndex() + "created", Snackbar.LENGTH_LONG);
                                snackbar.show();
                            } catch (RuntimeError e) {
                                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                                snackbar.show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.deleteShare.setOnClickListener(view1 -> {
            ProgressBar pb = binding.resetAccountProgress;
            pb.setVisibility(View.VISIBLE);
            try {
                ArrayList<String> indexes = activity.appKey.getShareIndexes();
                String index = indexes.get(indexes.size() - 1);
                activity.appKey.deleteShare(index, result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Void>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.getMessage(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        binding.resetAccount.setEnabled(true);
                        Snackbar snackbar;
                        snackbar = Snackbar.make(view1, index + " deleted", Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            } catch (RuntimeError | JSONException e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            } finally {
                pb.setVisibility(View.GONE);
            }
        });

        binding.addPassword.setOnClickListener(view1 -> {
            try {
                SecurityQuestionModule.generateNewShare(activity.appKey, "What is the name of your cat?", "blublu", result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<GenerateShareStoreResult>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                GenerateShareStoreResult share = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<GenerateShareStoreResult>) result).data;
                                String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                                binding.addPassword.setEnabled(false);
                                Snackbar snackbar = Snackbar.make(view1, "Added password " + answer + " for share index" + share.getIndex(), Snackbar.LENGTH_LONG);
                                snackbar.show();
                            } catch (RuntimeError e) {
                                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                                snackbar.show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.changePassword.setOnClickListener(view1 -> {
            try {
                SecurityQuestionModule.changeSecurityQuestionAndAnswer(activity.appKey, "What is the name of your cat?", "Blublu", result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                Boolean changed = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                                if (changed) {
                                    String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                                    binding.changePassword.setEnabled(false);
                                    Snackbar snackbar = Snackbar.make(view1, "Password changed to" + answer, Snackbar.LENGTH_LONG);
                                    snackbar.show();
                                } else {
                                    Snackbar snackbar = Snackbar.make(view1, "Password failed ot be changed", Snackbar.LENGTH_LONG);
                                    snackbar.show();
                                }
                            } catch (RuntimeError e) {
                                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                                snackbar.show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.showPassword.setOnClickListener(view1 -> {
            try {
                String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, "Password currently is" + answer, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.setSeedPhrase.setOnClickListener(view1 -> {
            String phrase = "seed sock milk update focus rotate barely fade car face mechanic mercy";
            SeedPhraseModule.setSeedPhrase(activity.appKey, "HD Key Tree", phrase, 0, result -> {
                if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                    requireActivity().runOnUiThread(() -> {
                        Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                        Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                        snackbar.show();
                    });
                } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                    Boolean set = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                    Snackbar snackbar;
                    if (set) {
                        snackbar = Snackbar.make(view1, "Seed phrase set", Snackbar.LENGTH_LONG);
                    } else {
                        snackbar = Snackbar.make(view1, "Failed to set seed phrase", Snackbar.LENGTH_LONG);
                    }
                    snackbar.show();
                }
            });
        });

        binding.changeSeedPhrase.setOnClickListener(view1 -> {
            String oldPhrase = "seed sock milk update focus rotate barely fade car face mechanic mercy";
            String newPhrase = "object brass success calm lizard science syrup planet exercise parade honey impulse";
            SeedPhraseModule.changePhrase(activity.appKey, oldPhrase, newPhrase, result -> {
                if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                    requireActivity().runOnUiThread(() -> {
                        Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                        Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                        snackbar.show();
                    });
                } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                    Boolean changed = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                    if (changed) {
                        Snackbar snackbar = Snackbar.make(view1, "Seed phrase changed", Snackbar.LENGTH_LONG);
                        snackbar.show();
                        binding.deleteSeedPhrase.setEnabled(true);
                    } else {
                        Snackbar snackbar = Snackbar.make(view1, "Failed to change seed phrase", Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                }
            });
        });

        binding.getSeedPhrase.setOnClickListener(view1 -> {
            try {
                String phrases = SeedPhraseModule.getPhrases(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, phrases, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.resetAccount.setOnClickListener(view1 -> {
            try {
//                delete locally stored share
                StorageLayer temp_sl = new StorageLayer(false, "https://metadata.tor.us", 2);
                ServiceProvider temp_sp = new ServiceProvider(false, activity.postboxKey);
                ThresholdKey temp_key = new ThresholdKey(null, null, temp_sl, temp_sp, null, null, false, false);

                activity.sharedpreferences.edit().clear().commit();

                temp_key.storage_layer_set_metadata(activity.postboxKey, "{ \"message\": \"KEY_NOT_FOUND\" }", result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        activity.runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Void>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred here: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        activity.runOnUiThread(() -> {
                            activity.resetState();
                            binding.googleLogin.setEnabled(true);
                            binding.createThresholdKey.setEnabled(true);
                            binding.reconstructThresholdKey.setEnabled(true);
                            binding.generateNewShare.setEnabled(false);
                            binding.deleteShare.setEnabled(false);
                            binding.deleteSeedPhrase.setEnabled(false);
                            binding.resultView.setText("");
                            Snackbar snackbar = Snackbar.make(view1, "Account reset successful", Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });

                    }
                });

                activity.postboxKey = null;

            } catch (RuntimeError e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.getMessage(), Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.deleteSeedPhrase.setOnClickListener(view1 -> {
            try {
                String newPhrase = "object brass success calm lizard science syrup planet exercise parade honey impulse";
                SeedPhraseModule.deletePhrase(activity.appKey, newPhrase, result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        Boolean deleted = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                        Snackbar snackbar;
                        if (deleted) {
                            snackbar = Snackbar.make(view1, "Phrase Deleted", Snackbar.LENGTH_LONG);
                        } else {
                            snackbar = Snackbar.make(view1, "Phrase failed ot be deleted", Snackbar.LENGTH_LONG);
                        }
                        snackbar.show();
                    }
                });
            } catch (Exception e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.exportShare.setOnClickListener(view1 -> activity.appKey.generateNewShare(result -> {
            if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                requireActivity().runOnUiThread(() -> {
                    Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<GenerateShareStoreResult>) result).exception;
                    Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                    snackbar.show();
                });
            } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                requireActivity().runOnUiThread(() -> {
                    try {
                        GenerateShareStoreResult shareStoreResult = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<GenerateShareStoreResult>) result).data;
                        String index = shareStoreResult.getIndex();
                        String share = activity.appKey.outputShare(index, null);
                        String serialized = ShareSerializationModule.serializeShare(activity.appKey, share, null);
                        Snackbar snackbar = Snackbar.make(view1, "Serialization result: " + serialized, Snackbar.LENGTH_LONG);
                        snackbar.show();
                    } catch (RuntimeError e) {
                        Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            }
        }));

        binding.setPrivateKey.setOnClickListener(view1 -> {
            try {
                PrivateKey newKey = PrivateKey.generate();
                PrivateKeysModule.setPrivateKey(activity.appKey, newKey.hex, "secp256k1n", result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        Boolean set = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                        Snackbar snackbar = Snackbar.make(view1, "Set private key result: " + set, Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            } catch (RuntimeError e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.getPrivateKey.setOnClickListener(view1 -> {
            try {
                String key = PrivateKeysModule.getPrivateKeys(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, key, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.getAccounts.setOnClickListener(view1 -> {
            try {
                ArrayList<String> accounts = PrivateKeysModule.getPrivateKeyAccounts(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, accounts.toString(), Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError | JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void renderError(Throwable error) {
        Throwable reason = Helpers.unwrapCompletionException(error);
        TextView textView = binding.resultView;
        if (reason instanceof UserCancelledException || reason instanceof NoAllowedBrowserFoundException) {
            textView.setText(error.getMessage());
        }
        else {
            String errorMessage = getResources().getString(R.string.error_message, error.getMessage());
            textView.setText(errorMessage);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}