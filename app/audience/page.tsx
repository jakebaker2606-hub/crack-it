"use client";

import { useEffect, useRef, useState } from "react";

import { motion } from "framer-motion";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
 update,
} from "firebase/database";

const wheelOptions = [
  "DOUBLE POINTS",
  "LOSE 500",
  "BONUS ROUND",
  "TEAM SWAP",
  "TRIPLE POINTS",
  "STEAL POINTS",
  "MYSTERY",
  "FREE 1000",
];

export default function AudiencePage() {

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,

    question: "",

    answer: "",

    showAnswer: false,

    timer: 30,

    timerRunning: false,

    showTimer: false,

    showChaosWheel: false,

    chaosResult: "",

    showLeaderboard: false,

    winner: "",

    showIntro: false,

    endGame: false,
  });

  const [rotation, setRotation] = useState(0);

  const [spinning, setSpinning] = useState(false);

  const timerAudio = useRef<HTMLAudioElement | null>(null);

  /* AUDIO */

  useEffect(() => {

    timerAudio.current = new Audio("/sounds/timer.mp3");

    timerAudio.current.loop = true;

  }, []);

  /* FIREBASE */

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, async (snapshot) => {

      const data = snapshot.val();

      if (!data) return;

      setGameState(data);

      /* TIMER AUDIO */

      if (data.timerRunning) {

        try {

          await timerAudio.current?.play();

        } catch (err) {}

      } else {

        timerAudio.current?.pause();

        if (timerAudio.current) {
          timerAudio.current.currentTime = 0;
        }

      }

      /* AUTO SPIN */

      if (
        data.showChaosWheel &&
        !data.chaosResult &&
        !spinning
      ) {

        spinWheel();

      }

    });

    return () => unsubscribe();

  }, [spinning]);

  /* TIMER */

  useEffect(() => {

    let interval: any;

    if (
      gameState.timerRunning &&
      gameState.timer > 0
    ) {

      interval = setInterval(async () => {

        await update(ref(db, "game"), {
          timer: gameState.timer - 1,
        });

      }, 1000);

    }

    if (
      gameState.timerRunning &&
      gameState.timer <= 0
    ) {

      update(ref(db, "game"), {
        timerRunning: false,
        showTimer: false,
      });

    }

    return () => clearInterval(interval);

  }, [
    gameState.timerRunning,
    gameState.timer,
  ]);

  /* FORMAT SCORE */

  const formatScore = (score: number) => {

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score.toString();

  };

  /* SPIN */

  const spinWheel = async () => {

    if (spinning) return;

    setSpinning(true);

    const randomIndex = Math.floor(
      Math.random() * wheelOptions.length
    );

    const selected =
      wheelOptions[randomIndex];

    const degreesPerOption =
      360 / wheelOptions.length;

    const stopRotation =
      3600 +
      (360 - randomIndex * degreesPerOption);

    setRotation(stopRotation);

    setTimeout(async () => {

      await update(ref(db, "game"), {
        chaosResult: selected,
      });

      setSpinning(false);

    }, 5000);

  };

  /* INTRO */

  if (gameState.showIntro) {

    return (

      <div className="min-h-screen bg-black flex items-center justify-center relative overflow-hidden">

        <video
          autoPlay
          muted
          loop
          playsInline
          className="absolute inset-0 w-full h-full object-cover opacity-30"
        >
          <source
            src="/videos/background.mp4"
            type="video/mp4"
          />
        </video>

        <div className="absolute inset-0 bg-black/60" />

        <motion.h1
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ duration: 1 }}
          className="text-yellow-400 text-9xl font-black z-10"
        >
          CRACK IT!
        </motion.h1>

      </div>

    );

  }

  /* END GAME */

  if (gameState.endGame) {

    return (

      <div className="min-h-screen bg-black flex items-center justify-center">

        <motion.h1
          animate={{
            scale: [1, 1.1, 1],
          }}
          transition={{
            repeat: Infinity,
            duration: 1,
          }}
          className="text-red-500 text-[140px] font-black"
        >
          GAME OVER
        </motion.h1>

      </div>

    );

  }

  return (

    <div className="min-h-screen bg-black text-white relative overflow-hidden">

      {/* VIDEO */}

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30 z-0"
      >
        <source
          src="/videos/background.mp4"
          type="video/mp4"
        />
      </video>

      <div className="fixed inset-0 bg-black/60 z-0" />

      {/* MAIN */}

      <div className="relative z-10 p-10">

        {/* TITLE */}

        <h1 className="text-center text-yellow-400 text-9xl font-black">
          CRACK IT!
        </h1>

        {/* TIMER */}

        {gameState.showTimer && (

          <div className="flex justify-center mt-6">

            <motion.div
              animate={{
                scale: gameState.timerRunning
                  ? [1, 1.08, 1]
                  : 1,
              }}
              transition={{
                repeat: Infinity,
                duration: 1,
              }}
              className="bg-red-600 text-white text-7xl font-black px-14 py-6 rounded-3xl shadow-[0_0_40px_red]"
            >
              {gameState.timer}
            </motion.div>

          </div>

        )}

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
          className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10"
        >
          {gameState.question || "Waiting for next question..."}
        </motion.div>

        {/* ANSWER */}

        {gameState.showAnswer &&
          gameState.answer && (

          <motion.div
            initial={{
              scale: 0,
              opacity: 0,
            }}
            animate={{
              scale: 1,
              opacity: 1,
            }}
            className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6"
          >
            ANSWER: {gameState.answer}
          </motion.div>

        )}

        {/* TEAMS */}

        <div className="flex justify-center gap-10 flex-wrap mt-14">

          {[
            {
              name: "TEAM A",
              score: gameState.teamA,
              color: "bg-blue-600",
            },
            {
              name: "TEAM B",
              score: gameState.teamB,
              color: "bg-pink-600",
            },
            {
              name: "TEAM C",
              score: gameState.teamC,
              color: "bg-green-600",
            },
          ].map((team) => (

            <motion.div
              key={team.name}
              animate={{
                y: [0, -10, 0],
              }}
              transition={{
                repeat: Infinity,
                duration: 3,
              }}
              className={`${team.color} w-[320px] h-[420px] rounded-3xl flex flex-col items-center justify-center shadow-2xl`}
            >

              <div className="text-5xl font-black">
                {team.name}
              </div>

              <div className="text-[90px] font-black mt-10">
                {formatScore(team.score)}
              </div>

            </motion.div>

          ))}

        </div>

      </div>

      {/* CHAOS ROUND */}

      {gameState.showChaosWheel && (

        <div className="fixed inset-0 bg-black/90 z-50 flex flex-col items-center justify-center">

          <h1 className="text-yellow-400 text-8xl font-black mb-10">
            CHAOS ROUND
          </h1>

          {/* POINTER */}

          <div className="absolute top-[120px] z-20 w-0 h-0 border-l-[40px] border-r-[40px] border-b-[80px] border-l-transparent border-r-transparent border-b-yellow-400" />

          {/* WHEEL */}

          <motion.div
            animate={{
              rotate: rotation,
            }}
            transition={{
              duration: 5,
              ease: "easeOut",
            }}
            className="relative w-[700px] h-[700px] rounded-full border-[16px] border-blue-800"
            style={{
              background: `
                conic-gradient(
                  #ec4899 0deg 45deg,
                  #f97316 45deg 90deg,
                  #ef4444 90deg 135deg,
                  #d946ef 135deg 180deg,
                  #22c55e 180deg 225deg,
                  #eab308 225deg 270deg,
                  #0ea5e9 270deg 315deg,
                  #9333ea 315deg 360deg
                )
              `,
            }}
          >

            {/* LABELS */}

            {wheelOptions.map((option, index) => {

              const angle = index * 45;

              return (

                <div
                  key={option}
                  className="absolute left-1/2 top-1/2 text-white font-black text-xl"
                  style={{
                    transform: `
                      rotate(${angle + 22.5}deg)
                      translateY(-260px)
                      rotate(90deg)
                    `,
                    transformOrigin: "center",
                  }}
                >
                  {option}
                </div>

              );

            })}

            {/* CENTER */}

            <div className="absolute inset-0 flex items-center justify-center">

              <div className="w-[180px] h-[180px] rounded-full bg-blue-950 border-8 border-blue-700 flex items-center justify-center text-5xl font-black">
                CHAOS
              </div>

            </div>

          </motion.div>

          {/* RESULT */}

          {gameState.chaosResult && (

            <motion.div
              initial={{
                scale: 0,
                opacity: 0,
              }}
              animate={{
                scale: 1,
                opacity: 1,
              }}
              className="mt-10 bg-black border-4 border-yellow-400 px-14 py-8 rounded-3xl"
            >

              <div className="text-white text-3xl font-black text-center">
                RESULT
              </div>

              <div className="text-yellow-400 text-6xl font-black text-center mt-4">
                {gameState.chaosResult}
              </div>

            </motion.div>

          )}

        </div>

      )}

      {/* LEADERBOARD */}

      {gameState.showLeaderboard && (

        <div className="fixed inset-0 bg-black/95 z-[90] flex flex-col items-center justify-center">

          <h1 className="text-yellow-400 text-8xl font-black mb-16">
            LEADERBOARD
          </h1>

          <div className="flex gap-10">

            {[
              {
                name: "TEAM A",
                score: gameState.teamA,
                color: "bg-blue-600",
              },
              {
                name: "TEAM B",
                score: gameState.teamB,
                color: "bg-pink-600",
              },
              {
                name: "TEAM C",
                score: gameState.teamC,
                color: "bg-green-600",
              },
            ]
              .sort((a, b) => b.score - a.score)
              .map((team, index) => (

                <div
                  key={team.name}
                  className={`${team.color} w-[320px] h-[420px] rounded-3xl flex flex-col items-center justify-center`}
                >

                  <div className="text-7xl font-black">
                    #{index + 1}
                  </div>

                  <div className="text-5xl font-black mt-6">
                    {team.name}
                  </div>

                  <div className="text-8xl font-black mt-10">
                    {formatScore(team.score)}
                  </div>

                </div>

              ))}

          </div>

        </div>

      )}

      {/* WINNER */}

      {gameState.winner && (

        <div className="fixed inset-0 bg-black/90 z-[100] flex items-center justify-center">

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

            <div className="text-yellow-400 text-[140px] font-black">
              {gameState.winner}
            </div>

            <div className="text-white text-7xl font-black">
              WINS!
            </div>

          </motion.div>

        </div>

      )}

    </div>

  );

}