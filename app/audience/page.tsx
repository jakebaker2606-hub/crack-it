"use client";

import { useEffect, useRef, useState } from "react";

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
  "STEAL POINTS",
  "FREE 1000",
  "MYSTERY",
  "TRIPLE POINTS",
  "SWAP SCORES",
];

export default function AudiencePage() {

  const [game, setGame] = useState<any>({
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
    winner: "",
  });

  const [rotation, setRotation] = useState(0);

  const [spinning, setSpinning] = useState(false);

  const timerAudio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {

    timerAudio.current = new Audio("/sounds/timer.mp3");

    timerAudio.current.loop = true;

  }, []);

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, async (snapshot) => {

      const data = snapshot.val();

      if (!data) return;

      setGame(data);

      if (data.timerRunning) {

        try {
          await timerAudio.current?.play();
        } catch {}

      } else {

        timerAudio.current?.pause();

        if (timerAudio.current) {
          timerAudio.current.currentTime = 0;
        }

      }

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

  useEffect(() => {

    let interval: any;

    if (
      game.timerRunning &&
      game.timer > 0
    ) {

      interval = setInterval(async () => {

        await update(ref(db, "game"), {
          timer: game.timer - 1,
        });

      }, 1000);

    }

    return () => clearInterval(interval);

  }, [
    game.timerRunning,
    game.timer,
  ]);

  const spinWheel = async () => {

    setSpinning(true);

    const randomIndex = Math.floor(
      Math.random() * wheelOptions.length
    );

    const selected = wheelOptions[randomIndex];

    const degreesPerOption =
      360 / wheelOptions.length;

    const finalRotation =
      3600 +
      (360 - randomIndex * degreesPerOption);

    setRotation(finalRotation);

    setTimeout(async () => {

      await update(ref(db, "game"), {
        chaosResult: selected,
      });

      setSpinning(false);

    }, 5000);

  };

  const formatScore = (score: number) => {

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score;

  };

  return (

    <div className="min-h-screen bg-black text-white p-10 relative overflow-hidden">

      <h1 className="text-yellow-400 text-8xl font-black text-center">
        CRACK IT!
      </h1>

      {/* TIMER */}

      {game.showTimer && (

        <div className="flex justify-center mt-8">

          <div className="bg-red-600 text-white text-7xl font-black px-16 py-8 rounded-3xl">
            {game.timer}
          </div>

        </div>

      )}

      {/* QUESTION */}

      <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10">
        {game.question || "Waiting for next question..."}
      </div>

      {/* ANSWER */}

      {game.showAnswer && (

        <div className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6">
          ANSWER: {game.answer}
        </div>

      )}

      {/* TEAMS */}

      <div className="flex justify-center gap-10 mt-16 flex-wrap">

        <div className="bg-blue-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM A
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamA || 0)}
          </div>

        </div>

        <div className="bg-pink-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM B
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamB || 0)}
          </div>

        </div>

        <div className="bg-green-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM C
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamC || 0)}
          </div>

        </div>

      </div>

      {/* CHAOS WHEEL */}

      {game.showChaosWheel && (

        <div className="fixed inset-0 bg-black/80 flex flex-col items-center justify-center z-50">

          <div
            style={{
              transform: `rotate(${rotation}deg)`,
              transition: "transform 5s ease-out",
            }}
            className="w-[500px] h-[500px] rounded-full border-[20px] border-white relative overflow-hidden"
          >

            {wheelOptions.map((option, index) => {

              const angle =
                (360 / wheelOptions.length) * index;

              return (

                <div
                  key={option}
                  style={{
                    transform: `rotate(${angle}deg)`,
                    transformOrigin: "bottom center",
                  }}
                  className="absolute w-1/2 h-1/2 left-1/2 top-1/2 bg-purple-600 border border-black flex items-center justify-center text-center text-xl font-black"
                >
                  {option}
                </div>

              );

            })}

          </div>

          {game.chaosResult && (

            <div className="bg-yellow-400 text-black text-6xl font-black px-12 py-6 rounded-3xl mt-10">
              {game.chaosResult}
            </div>

          )}

        </div>

      )}

      {/* WINNER */}

      {game.winner && (

        <div className="fixed inset-0 bg-black/90 flex items-center justify-center z-50">

          <div className="text-yellow-400 text-9xl font-black text-center">
            {game.winner}
            <br />
            WINS!
          </div>

        </div>

      )}

    </div>

  );

}