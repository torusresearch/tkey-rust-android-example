package com.example.tkey_android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.tkey_android.databinding.FragmentFirstBinding;
import com.google.android.material.snackbar.Snackbar;
import com.web3auth.tkey.RuntimeError;
import com.web3auth.tkey.ThresholdKey.Common.PrivateKey;
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
import org.torusresearch.customauth.types.Auth0ClientOptions;
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
import java.util.concurrent.CompletableFuture;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private LoginVerifier selectedLoginVerifier;
    private CustomAuth torusSdk;
    private BigInteger postBoxKey = null;

    private final String[] allowedBrowsers = new String[]{
            "com.android.chrome", // Chrome stable
            "com.google.android.apps.chrome", // Chrome system
            "com.android.chrome.beta", // Chrome beta
    };

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (((MainActivity) requireActivity()).appKey != null) {
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
                MainActivity activity = ((MainActivity) requireActivity());
                selectedLoginVerifier = new LoginVerifier("Google", LoginType.GOOGLE, "221898609709-obfn3p63741l5333093430j3qeiinaa8.apps.googleusercontent.com", "google-lrc");

                Auth0ClientOptions.Auth0ClientOptionsBuilder builder = null;
                if (this.selectedLoginVerifier.getDomain() != null) {
                    builder = new Auth0ClientOptions.Auth0ClientOptionsBuilder(this.selectedLoginVerifier.getDomain());
                    builder.setVerifierIdField(this.selectedLoginVerifier.getVerifierIdField());
                    builder.setVerifierIdCaseSensitive(this.selectedLoginVerifier.isVerfierIdCaseSensitive());
                }
                CompletableFuture<TorusLoginResponse> torusLoginResponseCf;
                if (builder == null) {
                    torusLoginResponseCf = this.torusSdk.triggerLogin(new SubVerifierDetails(this.selectedLoginVerifier.getTypeOfLogin(),
                            this.selectedLoginVerifier.getVerifier(),
                            this.selectedLoginVerifier.getClientId())
                            .setPreferCustomTabs(true)
                            .setAllowedBrowsers(allowedBrowsers));
                } else {
                    torusLoginResponseCf = this.torusSdk.triggerLogin(new SubVerifierDetails(this.selectedLoginVerifier.getTypeOfLogin(),
                            this.selectedLoginVerifier.getVerifier(),
                            this.selectedLoginVerifier.getClientId(), builder.build())
                            .setPreferCustomTabs(true)
                            .setAllowedBrowsers(allowedBrowsers));
                }
                binding.createThresholdKey.setEnabled(true);

                torusLoginResponseCf.whenComplete((torusLoginResponse, error) -> {
                    if (error != null) {
                        renderError(error);
                    } else {
                        String publicAddress = torusLoginResponse.getPublicAddress();
                        this.postBoxKey = torusLoginResponse.getPrivateKey();
                        binding.resultView.setText(publicAddress);
                        // when I try to set enable true here, button does not enabled even if login succeeded
//                        binding.createThresholdKey.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        binding.createThresholdKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                activity.tkeyStorage = new StorageLayer(false, "https://metadata.tor.us", 2);
                activity.tkeyProvider = new ServiceProvider(false, this.postBoxKey.toString(16));
                activity.appKey = new ThresholdKey(null, null, activity.tkeyStorage, activity.tkeyProvider, null, null, false, false);
                PrivateKey key = PrivateKey.generate();
                activity.appKey.initialize(key.hex, null, false, false, result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<KeyDetails>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();

                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        KeyDetails details = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<KeyDetails>) result).data;
                        activity.appKey.reconstruct(reconstruct_result -> {
                            if (reconstruct_result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                                requireActivity().runOnUiThread(() -> {
                                    Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<KeyReconstructionDetails>) reconstruct_result).exception;
                                    Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                                    snackbar.show();
                                });
                            } else if (reconstruct_result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                                KeyReconstructionDetails reconstructionDetails = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<KeyReconstructionDetails>) reconstruct_result).data;
                                requireActivity().runOnUiThread(() -> {
                                    try {
                                        binding.resultView.setText("");
                                        binding.resultView.append("Final Key\n");
                                        binding.resultView.append(reconstructionDetails.getKey() + "\n");
                                        binding.resultView.append("Total Shares" + details.getTotalShares() + "\n");
                                        binding.resultView.append("Required Shares" + details.getThreshold() + "\n");
                                        binding.createThresholdKey.setEnabled(false);
                                        binding.reconstructThresholdKey.setEnabled(true);
                                    } catch (RuntimeError e) {
                                        Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                                        snackbar.show();
                                    }
                                });
                            }
                        });
                    }
                });
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.reconstructThresholdKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                activity.appKey.reconstruct(result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<KeyReconstructionDetails>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                KeyReconstructionDetails details = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<KeyReconstructionDetails>) result).data;
                                binding.generateNewShare.setEnabled(true);
                                Snackbar snackbar = Snackbar.make(view1, details.getKey(), Snackbar.LENGTH_LONG);
                                snackbar.show();
                            } catch (RuntimeError e) {
                                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                                snackbar.show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        binding.generateNewShare.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
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
                throw new RuntimeException(e);
            }
        });

        binding.deleteShare.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                ArrayList<String> indexes = activity.appKey.getShareIndexes();
                String index = indexes.get(indexes.size() - 1);
                activity.appKey.deleteShare(index, result -> {
                    if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Error) {
                        requireActivity().runOnUiThread(() -> {
                            Exception e = ((com.web3auth.tkey.ThresholdKey.Common.Result.Error<Boolean>) result).exception;
                            Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e.toString(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        });
                    } else if (result instanceof com.web3auth.tkey.ThresholdKey.Common.Result.Success) {
                        Boolean deleted = ((com.web3auth.tkey.ThresholdKey.Common.Result.Success<Boolean>) result).data;
                        binding.deleteShare.setEnabled(true);
                        Snackbar snackbar;
                        if (deleted) {
                            snackbar = Snackbar.make(view1, index + " deleted", Snackbar.LENGTH_LONG);
                        } else {
                            snackbar = Snackbar.make(view1, index + " failed to be deleted", Snackbar.LENGTH_LONG);
                        }
                        snackbar.show();
                    }
                });
            } catch (RuntimeError | JSONException e) {
                Snackbar snackbar = Snackbar.make(view1, "A problem occurred: " + e, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        binding.addPassword.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
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
                throw new RuntimeException(e);
            }
        });

        binding.changePassword.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
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
                throw new RuntimeException(e);
            }
        });

        binding.showPassword.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, "Password currently is" + answer, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.setSeedPhrase.setOnClickListener(view1 -> {
            MainActivity activity = ((MainActivity) requireActivity());
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
            MainActivity activity = ((MainActivity) requireActivity());
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
                MainActivity activity = ((MainActivity) requireActivity());
                String phrases = SeedPhraseModule.getPhrases(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, phrases, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.deleteSeedPhrase.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
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
                throw new RuntimeException(e);
            }
        });

        binding.exportShare.setOnClickListener(view1 -> {
            MainActivity activity = ((MainActivity) requireActivity());
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
            });
        });

        binding.setPrivateKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                PrivateKey key = PrivateKey.generate();
                PrivateKeysModule.setPrivateKey(activity.appKey, key.hex, "secp256k1n", result -> {
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
                throw new RuntimeException(e);
            }
        });

        binding.getPrivateKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                String key = PrivateKeysModule.getPrivateKeys(activity.appKey);
                Snackbar snackbar = Snackbar.make(view1, key, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.getAccounts.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
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