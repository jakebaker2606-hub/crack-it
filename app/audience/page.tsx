"use client";

import { useEffect, useState } from "react";

import { ref, onValue } from "firebase/database";

import { db } from "../../lib/firebase";

import { motion, AnimatePresence } from "framer-motion";

export default function AudiencePage() {

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,

    teamAName: "Team A",
    teamBName: "Team B",

    teamAAvatar: "🦊",
    teamBAvatar: "🐼",

    round: 1,

    question: "Waiting for next question...",
    answer: "",

    revealAnswer: false,

    category: "",

    chaos: "",

    timer: 0,

    wheelResult: "",
    showWheel: false,

    winner: "",

    showIntro: false,
    showGameOver: false,

    hypeMessage: "",

    suddenDeath: false,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

    return () => unsubscribe();

  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-black to-gray-900 text-white p-10 overflow-hidden">

      {gameState.showIntro && (

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black flex items-center justify-center z-50"
        >

          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1.2 }}
            transition={{
              repeat: Infinity,
              repeatType: "reverse",
              duration: 1
            }}
            className="text-yellow-400 text-9xl font-black"
          >
            CRACK IT!
          </motion.div>

        </motion.div>

      )}

      {gameState.showGameOver && (

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black flex flex-col items-center justify-center z-50"
        >

          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1.2 }}
            transition={{
              repeat: Infinity,
              repeatType: "reverse",
              duration: 0.6
            }}
            className="text-red-500 text-9xl font-black"
          >
            GAME OVER
          </motion.div>

        </motion.div>

      )}

      <div className="text-center max-w-6xl w-full">

        {gameState.suddenDeath && (

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{
              repeat: Infinity,
              repeatType: "reverse",
              duration: 0.3
            }}
            className="bg-red-700 text-white p-8 rounded-3xl text-7xl font-black mb-8"
          >
            SUDDEN DEATH
          </motion.div>

        )}

        <motion.div
          initial={{ y: -100 }}
          animate={{ y: 0 }}
          className="bg-purple-600 p-4 rounded-3xl text-4xl font-black mb-6 inline-block"
        >
          ROUND {gameState.round}
        </motion.div>

        <motion.h1
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          className="text-8xl font-black text-yellow-400 mb-10"
        >
          CRACK IT!
        </motion.h1>

        <div className="text-3xl font-black text-purple-400 mb-4">
          {gameState.category}
        </div>

        <AnimatePresence mode="wait">

          <motion.div
            key={gameState.question}
            initial={{ opacity: 0, y: 80 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5 }}
            className="bg-yellow-400 text-black p-10 rounded-3xl text-5xl font-black mb-10 shadow-2xl"
          >
            {gameState.question}
          </motion.div>

        </AnimatePresence>

        {gameState.revealAnswer && (

          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            className="bg-green-500 text-black p-8 rounded-3xl text-5xl font-black mb-10"
          >
            ANSWER: {gameState.answer}
          </motion.div>

        )}

        {gameState.timer > 0 && (

          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1.1 }}
            transition={{
              repeat: Infinity,
              repeatType: "reverse",
              duration: 0.5
            }}
            className="bg-green-500 text-black p-8 rounded-full text-7xl font-black mb-10 w-48 h-48 flex items-center justify-center mx-auto"
          >
            {gameState.timer}
          </motion.div>

        )}

        {gameState.showWheel && (

          <motion.div
            initial={{ rotate: 0 }}
            animate={{ rotate: 1440 }}
            transition={{ duration: 4 }}
            className="bg-purple-600 p-16 rounded-full w-[450px] h-[450px] mx-auto flex items-center justify-center text-5xl font-black mb-10 border-8 border-yellow-400"
          >
            {gameState.wheelResult}
          </motion.div>

        )}

        {gameState.winner && (

          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1.2 }}
            transition={{
              repeat: Infinity,
              repeatType: "reverse",
              duration: 0.4
            }}
            className="fixed inset-0 bg-yellow-400 flex items-center justify-center text-black text-8xl font-black z-50"
          >
            🎉 {gameState.winner} WINS 🎉
          </motion.div>

        )}

        <div className="flex gap-10 justify-center">

          <motion.div
            animate={{ scale: [1, 1.05, 1] }}
            transition={{ repeat: Infinity, duration: 2 }}
            className="bg-blue-600 p-10 rounded-3xl min-w-[300px] shadow-2xl"
          >

            <div className="text-7xl mb-4">
              {gameState.teamAAvatar}
            </div>

            <div className="text-4xl font-black">
              {gameState.teamAName}
            </div>

            <div className="text-7xl font-black mt-4">
              <motion.div
                key={gameState.teamA}
                initial={{ scale: 1.8 }}
                animate={{ scale: 1 }}
              >
                {gameState.teamA}
              </motion.div>
            </div>

          </motion.div>

          <motion.div
            animate={{ scale: [1, 1.05, 1] }}
            transition={{ repeat: Infinity, duration: 2 }}
            className="bg-pink-600 p-10 rounded-3xl min-w-[300px] shadow-2xl"
          >

            <div className="text-7xl mb-4">
              {gameState.teamBAvatar}
            </div>

            <div className="text-4xl font-black">
              {gameState.teamBName}
            </div>

            <div className="text-7xl font-black mt-4">
              <motion.div
                key={gameState.teamB}
                initial={{ scale: 1.8 }}
                animate={{ scale: 1 }}
              >
                {gameState.teamB}
              </motion.div>
            </div>

          </motion.div>

        </div>

      </div>

    </main>
  );
}