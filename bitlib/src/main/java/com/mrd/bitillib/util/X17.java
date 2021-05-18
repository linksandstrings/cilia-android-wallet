package com.mrd.bitillib.util;

import com.mrd.bitillib.cryptohash.*;

public class X17 {
    public static byte[] x17Digest(byte[] input, int offset, int length)
    {
        byte [] buf = new byte[length];
        for(int i = 0; i < length; ++i)
        {
            buf[i] = input[offset + i];
        }
        return x17(buf);
    }
    static byte [] x17(byte header[])
    {
        //Initialize
        Sha512Hash[] hash = new Sha512Hash[17];

        //Run the chain of algorithms
        BLAKE512 blake512 = new BLAKE512();
        hash[0] = new Sha512Hash(blake512.digest(header));

        BMW512 bmw = new BMW512();
        hash[1] = new Sha512Hash(bmw.digest(hash[0].getBytes()));

        Groestl512 groestl = new Groestl512();
        hash[2] = new Sha512Hash(groestl.digest(hash[1].getBytes()));

        Skein512 skein = new Skein512();
        hash[3] = new Sha512Hash(skein.digest(hash[2].getBytes()));

        JH512 jh = new JH512();
        hash[4] = new Sha512Hash(jh.digest(hash[3].getBytes()));

        Keccak512 keccak = new Keccak512();
        hash[5] = new Sha512Hash(keccak.digest(hash[4].getBytes()));

        Luffa512 luffa = new Luffa512();
        hash[6] = new Sha512Hash(luffa.digest(hash[5].getBytes()));

        CubeHash512 cubehash = new CubeHash512();
        hash[7] = new Sha512Hash(cubehash.digest(hash[6].getBytes()));

        SHAvite512 shavite = new SHAvite512();
        hash[8] = new Sha512Hash(shavite.digest(hash[7].getBytes()));

        SIMD512 simd = new SIMD512();
        hash[9] = new Sha512Hash(simd.digest(hash[8].getBytes()));

        ECHO512 echo = new ECHO512();
        hash[10] = new Sha512Hash(echo.digest(hash[9].getBytes()));

        Hamsi512 hamsi = new Hamsi512();
        hash[11] = new Sha512Hash(hamsi.digest(hash[10].getBytes()));

        Fugue512 fugue = new Fugue512();
        hash[12] = new Sha512Hash(fugue.digest(hash[11].getBytes()));

        Shabal512 shabal = new Shabal512();
        hash[13] = new Sha512Hash(shabal.digest(hash[12].getBytes()));

        Whirlpool whirlpool = new Whirlpool();
        hash[14] = new Sha512Hash(whirlpool.digest(hash[13].getBytes()));

        SHA512 sha = new SHA512();
        hash[15] = new Sha512Hash(sha.digest(hash[14].getBytes()));

        HAVAL256_5 haval256_5 = new HAVAL256_5();
        hash[16] = new Sha512Hash(haval256_5.digest(hash[15].getBytes()));

        return hash[16].trim256().getBytes();
    }
}
