package com.example.tkey_android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.tkey_android.databinding.FragmentFirstBinding;
import com.google.android.material.snackbar.Snackbar;
import com.web3auth.tkey.RuntimeError;
import com.web3auth.tkey.ThresholdKey.Common.PrivateKey;
import com.web3auth.tkey.ThresholdKey.Common.ShareStore;
import com.web3auth.tkey.ThresholdKey.GenerateShareStoreResult;
import com.web3auth.tkey.ThresholdKey.KeyDetails;
import com.web3auth.tkey.ThresholdKey.KeyReconstructionDetails;
import com.web3auth.tkey.ThresholdKey.Modules.PrivateKeysModule;
import com.web3auth.tkey.ThresholdKey.Modules.SecurityQuestionModule;
import com.web3auth.tkey.ThresholdKey.Modules.SeedPhraseModule;
import com.web3auth.tkey.ThresholdKey.Modules.ShareSerializationModule;
import com.web3auth.tkey.ThresholdKey.ServiceProvider;
import com.web3auth.tkey.ThresholdKey.ShareStorePolyIdIndexMap;
import com.web3auth.tkey.ThresholdKey.StorageLayer;
import com.web3auth.tkey.ThresholdKey.ThresholdKey;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
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
        binding.generateNewShare.setEnabled(false);
        binding.deleteShare.setEnabled(false);
        binding.deleteSeedPhrase.setEnabled(false);

        binding.buttonFirst.setOnClickListener(view1 -> NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment));

        binding.createThresholdKey.setOnClickListener(view1 -> {
            try {
                PrivateKey postBoxKey = PrivateKey.generate();
                MainActivity activity = ((MainActivity) requireActivity());
                activity.tkeyStorage = new StorageLayer(false, "https://metadata.tor.us", 2);
                activity.tkeyProvider = new ServiceProvider(false, postBoxKey.hex);
                activity.appKey = new ThresholdKey(null, null, activity.tkeyStorage, activity.tkeyProvider, null, null, false, false);
                PrivateKey key = PrivateKey.generate();
                KeyDetails details = activity.appKey.initialize(key.hex, null, false, false);
                KeyReconstructionDetails reconstructionDetails = activity.appKey.reconstruct();
                binding.resultView.setText("");
                binding.resultView.append("Final Key\n");
                binding.resultView.append(reconstructionDetails.getKey() + "\n");
                binding.resultView.append("Total Shares" + details.getTotalShares() + "\n");
                binding.resultView.append("Required Shares" + details.getThreshold() + "\n");
                binding.createThresholdKey.setEnabled(false);
                binding.reconstructThresholdKey.setEnabled(true);
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.reconstructThresholdKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                KeyReconstructionDetails details = activity.appKey.reconstruct();
                binding.generateNewShare.setEnabled(true);
                Snackbar snackbar = Snackbar.make(view1, details.getKey(), Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.generateNewShare.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                GenerateShareStoreResult share = activity.appKey.generateNewShare();
                binding.deleteShare.setEnabled(true);
                Snackbar snackbar = Snackbar.make(view1, share.getIndex() + "created", Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.deleteShare.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                String indexes = activity.appKey.getShareIndexes();
                JSONArray json = new JSONArray(indexes);
                activity.appKey.deleteShare(json.get(json.length()).toString());
                binding.deleteShare.setEnabled(true);
                Snackbar snackbar = Snackbar.make(view1, json.get(json.length()).toString() + " deleted", Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError | JSONException e) {
                throw new RuntimeException(e);
            }
        });

        binding.addPassword.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                GenerateShareStoreResult share = SecurityQuestionModule.generateNewShare(activity.appKey, "What is the name of your cat?", "blublu");
                String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                binding.addPassword.setEnabled(false);
                Snackbar snackbar = Snackbar.make(view1, "Added password" + answer, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.changePassword.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                SecurityQuestionModule.changeSecurityQuestionAndAnswer(activity.appKey, "What is the name of your cat?", "Blublu");
                String answer = SecurityQuestionModule.getAnswer(activity.appKey);
                binding.changePassword.setEnabled(false);
                Snackbar snackbar = Snackbar.make(view1, "Password changed to" + answer, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
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
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                String phrase = "seed sock milk update focus rotate barely fade car face mechanic mercy";
                SeedPhraseModule.setSeedPhrase(activity.appKey, "HD Key Tree", phrase, 0);
                Snackbar snackbar = Snackbar.make(view1, "Seed phrase set", Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.changeSeedPhrase.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                String oldPhrase = "seed sock milk update focus rotate barely fade car face mechanic mercy";
                String newPhrase = "object brass success calm lizard science syrup planet exercise parade honey impulse";
                SeedPhraseModule.changePhrase(activity.appKey, oldPhrase, newPhrase);
                Snackbar snackbar = Snackbar.make(view1, "Seed phrase changed", Snackbar.LENGTH_LONG);
                snackbar.show();
                binding.deleteSeedPhrase.setEnabled(true);
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
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
                SeedPhraseModule.deletePhrase(activity.appKey, newPhrase);
                Snackbar snackbar = Snackbar.make(view1, "Phrase Deleted", Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.exportShare.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                GenerateShareStoreResult shareStoreResult = activity.appKey.generateNewShare();
                String index = shareStoreResult.getIndex();
                String share = activity.appKey.outputShare(index, null);
                String serialized = ShareSerializationModule.serializeShare(activity.appKey, share, null);
                Snackbar snackbar = Snackbar.make(view1, "Serialization result: " + serialized, Snackbar.LENGTH_LONG);
                snackbar.show();
            } catch (RuntimeError e) {
                throw new RuntimeException(e);
            }
        });

        binding.setPrivateKey.setOnClickListener(view1 -> {
            try {
                MainActivity activity = ((MainActivity) requireActivity());
                PrivateKey key = PrivateKey.generate();
                boolean set = PrivateKeysModule.setPrivateKey(activity.appKey, key.hex, "secp256k1n");
                Snackbar snackbar = Snackbar.make(view1, "Set private key success: " + set, Snackbar.LENGTH_LONG);
                snackbar.show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}