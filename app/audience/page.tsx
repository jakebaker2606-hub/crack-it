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

  const [game, setGame] = useState<any>({});

  const [rotation, setRotation] = useState(0);

  const [spinning, setSpinning] = useState(false);

  const pageRef = useRef<HTMLDivElement | null>(null);

  const timerAudio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {

    timerAudio.current = new Audio("/sounds/timer.mp3");

    timerAudio.current.loop = true;

  }, []);

  /* FULLSCREEN */

  useEffect(() => {

    const goFullscreen = async () => {

      try {

        if (
          pageRef.current &&
          !document.fullscreenElement
        ) {

          await pageRef.current.requestFullscreen();

        }

      } catch {}

    };

    goFullscreen();

  }, []);

  /* FIREBASE */

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, async (snapshot) => {

      const data = snapshot.val();

      if (!data) return;

      setGame(data);

      /* TIMER AUDIO */

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

      /* CHAOS WHEEL */

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

  /* WHEEL */

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

  /* SCORE FORMAT */

  const formatScore = (score: number) => {

    if (!score) return 0;

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score;

  };

  const leaderboard = [
    {
      name: "TEAM A",
      score: game.teamA || 0,
      color: "bg-blue-600",
    },
    {
      name: "TEAM B",
      score: game.teamB || 0,
      color: "bg-pink-600",
    },
    {
      name: "TEAM C",
      score: game.teamC || 0,
      color: "bg-green-600",
    },
  ].sort((a, b) => b.score - a.score);

  return (

    <div
      ref={pageRef}
      className="min-h-screen bg-black text-white relative overflow-hidden"
    >

      {/* BACKGROUND VIDEO */}

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30"
      >
        <source
          src="/videos/background.mp4"
          type="video/mp4"
        />
      </video>

      <div className="fixed inset-0 bg-black/60" />

      {/* MAIN */}

      <div className="relative z-20 p-10">

        <h1 className="text-yellow-400 text-8xl font-black text-center">
          CRACK IT!
        </h1>

        {/* TIMER */}

        {game.showTimer && (

          <div className="flex justify-center mt-8">

            <div className="bg-red-600 text-white text-7xl font-black px-20 py-10 rounded-3xl animate-pulse">
              {game.timer}
            </div>

          </div>

        )}

        {/* QUESTION */}

        <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10 shadow-2xl animate-pulse">
          {game.question || "Waiting for next question..."}
        </div>

        {/* ANSWER */}

        {game.showAnswer && (

          <div className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6 shadow-2xl animate-bounce">
            ANSWER: {game.answer}
          </div>

        )}

        {/* TEAMS */}

        <div className="flex justify-center gap-10 mt-16 flex-wrap">

          {[
            {
              name: "TEAM A",
              score: game.teamA,
              color: "bg-blue-600",
            },
            {
              name: "TEAM B",
              score: game.teamB,
              color: "bg-pink-600",
            },
            {
              name: "TEAM C",
              score: game.teamC,
              color: "bg-green-600",
            },
          ].map((team) => (

            <div
              key={team.name}
              className={`${team.color} w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center shadow-2xl border-4 border-white`}
            >

              <div className="text-5xl font-black">
                {team.name}
              </div>

              <div className="text-8xl font-black mt-10">
                {formatScore(team.score)}
              </div>

            </div>

          ))}

        </div>

      </div>

      {/* INTRO */}

      {game.showIntro && (

        <div className="fixed inset-0 z-[200] bg-black flex items-center justify-center">

          <div className="text-center">

            <div className="text-yellow-400 text-9xl font-black animate-pulse">
              CRACK IT!
            </div>

            <div className="text-white text-4xl mt-10">
              THE ULTIMATE QUIZ SHOW
            </div>

          </div>

        </div>

      )}

      {/* LEADERBOARD */}

      {game.showLeaderboard && (

        <div className="fixed inset-0 z-[200] bg-black/90 flex items-center justify-center">

          <div className="bg-white text-black p-16 rounded-3xl w-[700px]">

            <h2 className="text-6xl font-black text-center mb-10">
              LEADERBOARD
            </h2>

            <div className="flex flex-col gap-5">

              {leaderboard.map((team, index) => (

                <div
                  key={team.name}
                  className={`${team.color} text-white p-6 rounded-2xl flex justify-between text-4xl font-black`}
                >

                  <div>
                    #{index + 1} {team.name}
                  </div>

                  <div>
                    {formatScore(team.score)}
                  </div>

                </div>

              ))}

            </div>

          </div>

        </div>

      )}

      {/* CHAOS WHEEL */}

      {game.showChaosWheel && (

        <div className="fixed inset-0 z-[200] bg-black/90 flex flex-col items-center justify-center">

          <div
            style={{
              transform: `rotate(${rotation}deg)`,
              transition: "transform 5s ease-out",
            }}
            className="w-[500px] h-[500px] rounded-full border-[20px] border-white relative"
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

        <div className="fixed inset-0 z-[200] bg-black/95 flex items-center justify-center">

          <div className="text-center">

            <div className="text-yellow-400 text-9xl font-black animate-pulse">
              {game.winner}
            </div>

            <div className="text-white text-6xl mt-10">
              ARE THE CHAMPIONS!
            </div>

          </div>

        </div>

      )}

      {/* END GAME */}

      {game.showEndGame && (

        <div className="fixed inset-0 z-[200] bg-black flex items-center justify-center">

          <div className="text-red-500 text-9xl font-black">
            GAME OVER
          </div>

        </div>

      )}

    </div>

  );

} 