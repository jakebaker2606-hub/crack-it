"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
} from "firebase/database";

export default function AudiencePage() {

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,

    teamAName: "Team A",
    teamBName: "Team B",
    teamCName: "Team C",

    teamAAvatar: "🦊",
    teamBAvatar: "🐼",
    teamCAvatar: "🐸",

    question: "Waiting for next question...",

    round: 1,

    winner: "",
  });

  const [showIntro, setShowIntro] = useState(true);

  useEffect(() => {

    const timer = setTimeout(() => {
      setShowIntro(false);
    }, 10000);

    return () => clearTimeout(timer);

  }, []);

  useEffect(() => {

    const gameRef = ref(db, "game");

    onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

  }, []);

  if (showIntro) {

    return (

      <div className="min-h-screen bg-black flex items-center justify-center overflow-hidden relative">

        {/* VIDEO */}

        <video
          autoPlay
          muted
          loop
          playsInline
          preload="auto"
          className="absolute inset-0 w-full h-full object-cover opacity-30"
        >
          <source src="/videos/background.mp4" type="video/mp4" />
        </video>

        {/* DARK OVERLAY */}

        <div className="absolute inset-0 bg-black/60" />

        {/* INTRO TITLE */}

        <motion.h1
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ duration: 1 }}
          className="text-yellow-400 text-9xl font-black z-10 drop-shadow-[0_0_30px_gold]"
        >
          CRACK IT!
        </motion.h1>

      </div>

    );

  }

  return (

    <div className="min-h-screen bg-black text-white overflow-hidden relative">

      {/* VIDEO BACKGROUND */}

      <video
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        className="fixed inset-0 w-full h-full object-cover z-0 opacity-30"
      >
        <source src="/videos/background.mp4" type="video/mp4" />
      </video>

      {/* OVERLAY */}

      <div className="fixed inset-0 bg-black/50 z-0" />

      {/* CONTENT */}

      <div className="relative z-10 p-10">

        {/* ROUND */}

        <div className="flex justify-center">

          <motion.div
            animate={{
              scale: [1, 1.08, 1],
            }}
            transition={{
              repeat: Infinity,
              duration: 2,
            }}
            className="bg-purple-600 px-10 py-4 rounded-3xl text-5xl font-black"
          >
            ROUND {gameState.round}
          </motion.div>

        </div>

        {/* TITLE */}

        <motion.h1
          animate={{
            scale: [1, 1.03, 1],
          }}
          transition={{
            repeat: Infinity,
            duration: 2,
          }}
          className="text-center text-yellow-400 text-9xl font-black mt-10 drop-shadow-[0_0_30px_gold]"
        >
          CRACK IT!
        </motion.h1>

        {/* QUESTION */}

        <motion.div
          key={gameState.question}
          initial={{
            opacity: 0,
            scale: 0.8,
          }}
          animate={{
            opacity: 1,
            scale: 1,
          }}
          className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10 shadow-2xl"
        >
          {gameState.question}
        </motion.div>

        {/* TEAMS */}

        <div className="flex justify-center gap-10 flex-wrap mt-14">

          {/* TEAM A */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
            }}
            className="w-[300px] h-[420px] bg-blue-600 rounded-3xl p-10 shadow-[0_0_40px_#3b82f6]"
          >

            <div className="text-8xl text-center">
              {gameState.teamAAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamAName}
            </div>

            <motion.div
              key={gameState.teamA}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-[120px] leading-none font-black mt-6"
            >
              {gameState.teamA}
            </motion.div>

          </motion.div>

          {/* TEAM B */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
              delay: 0.3,
            }}
            className="w-[300px] h-[420px] bg-pink-600 rounded-3xl p-10 shadow-[0_0_40px_#ec4899]"
          >

            <div className="text-8xl text-center">
              {gameState.teamBAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamBName}
            </div>

            <motion.div
              key={gameState.teamB}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-[120px] leading-none font-black mt-6"
            >
              {gameState.teamB}
            </motion.div>

          </motion.div>

          {/* TEAM C */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
              delay: 0.6,
            }}
            className="w-[300px] h-[420px] bg-green-600 rounded-3xl p-10 shadow-[0_0_40px_#22c55e]"
          >

            <div className="text-8xl text-center">
              {gameState.teamCAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamCName}
            </div>

            <motion.div
              key={gameState.teamC}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-[120px] leading-none font-black mt-6"
            >
              {gameState.teamC}
            </motion.div>

          </motion.div>

        </div>

        {/* WINNER SCREEN */}

        {gameState.winner && (

          <motion.div
            initial={{
              scale: 0,
              opacity: 0,
            }}
            animate={{
              scale: 1,
              opacity: 1,
            }}
            className="fixed inset-0 bg-black/90 flex items-center justify-center z-50"
          >

            <motion.div
              animate={{
                scale: [1, 1.08, 1],
              }}
              transition={{
                repeat: Infinity,
                duration: 1,
              }}
              className="text-center"
            >

              <div className="text-yellow-400 text-[140px] font-black drop-shadow-[0_0_40px_gold]">
                {gameState.winner}
              </div>

              <div className="text-white text-7xl font-black mt-4">
                WINS!
              </div>

            </motion.div>

          </motion.div>

        )}

      </div>

    </div>

  );

}